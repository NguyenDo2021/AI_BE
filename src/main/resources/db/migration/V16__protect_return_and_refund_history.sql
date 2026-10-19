-- Application lock order remains warehouse -> order -> document/actor -> products -> balances.
CREATE FUNCTION protect_sales_return_lines() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE doc sales_returns%ROWTYPE;
BEGIN
 SELECT * INTO doc FROM sales_returns WHERE id=CASE WHEN TG_OP='INSERT' THEN NEW.sales_return_id ELSE OLD.sales_return_id END FOR UPDATE;
 IF doc.status<>'DRAFT' THEN RAISE EXCEPTION 'Final return lines are immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND NEW.sales_return_id IS DISTINCT FROM OLD.sales_return_id THEN
  RAISE EXCEPTION 'Return line parent is immutable' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 IF NOT EXISTS(SELECT 1 FROM sales_order_lines l WHERE l.sales_order_id=doc.sales_order_id AND l.product_id=NEW.product_id
  AND l.quantity=NEW.sold_quantity AND l.product_code=NEW.product_code AND l.product_name=NEW.product_name AND l.unit=NEW.unit
  AND NEW.net_line_amount+CAST(NEW.allocated_discount_amount AS NUMERIC)=l.line_total) THEN
  RAISE EXCEPTION 'Invalid original sales line snapshot' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER sales_return_lines_guard BEFORE INSERT OR UPDATE OR DELETE ON sales_return_lines FOR EACH ROW EXECUTE FUNCTION protect_sales_return_lines();

CREATE FUNCTION protect_sales_return_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sale sales_orders%ROWTYPE; line_count BIGINT; line_sum NUMERIC;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Returns cannot be deleted' USING ERRCODE='23514'; END IF;
 SELECT * INTO sale FROM sales_orders WHERE id=NEW.sales_order_id FOR UPDATE;
 IF NEW.warehouse_id IS DISTINCT FROM sale.warehouse_id OR NEW.customer_id IS DISTINCT FROM sale.customer_id THEN
  RAISE EXCEPTION 'Invalid return source' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'DRAFT' OR NEW.version<>0 OR sale.status<>'CONFIRMED' OR sale.confirmed_at IS NULL THEN
   RAISE EXCEPTION 'Returns require confirmed sale and draft initial state' USING ERRCODE='23514';
  END IF;
 ELSE
  IF OLD.status<>'DRAFT' THEN RAISE EXCEPTION 'Confirmed/cancelled returns are immutable; adjustment unsupported' USING ERRCODE='23514'; END IF;
  IF NEW.id IS DISTINCT FROM OLD.id OR NEW.code IS DISTINCT FROM OLD.code
   OR NEW.sales_order_id IS DISTINCT FROM OLD.sales_order_id OR NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id
   OR NEW.customer_id IS DISTINCT FROM OLD.customer_id OR NEW.created_by IS DISTINCT FROM OLD.created_by
   OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.version<>OLD.version+1 THEN
   RAISE EXCEPTION 'Return identity/version is immutable' USING ERRCODE='23514';
  END IF;
  IF NEW.status='CANCELLED' AND
   (to_jsonb(NEW)-ARRAY['status','version','updated_by','updated_at','cancelled_by','cancelled_at','cancellation_reason'])
    IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','version','updated_by','updated_at','cancelled_by','cancelled_at','cancellation_reason']) THEN
   RAISE EXCEPTION 'Cancellation cannot change return contents' USING ERRCODE='23514';
  END IF;
  IF NEW.status='CONFIRMED' THEN
   IF sale.status<>'CONFIRMED' OR sale.confirmed_at IS NULL THEN RAISE EXCEPTION 'Source sale is no longer confirmed' USING ERRCODE='23514'; END IF;
   SELECT count(*),COALESCE(sum(CAST(return_amount AS NUMERIC)),0) INTO line_count,line_sum FROM sales_return_lines WHERE sales_return_id=NEW.id;
   IF line_count<1 OR line_count>1000 OR line_sum<>NEW.total_amount
    OR NEW.total_amount+COALESCE((SELECT sum(CAST(total_amount AS NUMERIC)) FROM sales_returns WHERE sales_order_id=sale.id AND status='CONFIRMED'),0)>sale.total_amount THEN
    RAISE EXCEPTION 'Invalid return total/lines' USING ERRCODE='23514';
   END IF;
   IF EXISTS(
    SELECT 1 FROM sales_return_lines l WHERE l.sales_return_id=NEW.id AND (
      l.quantity+COALESCE((SELECT sum(CAST(x.quantity AS NUMERIC)) FROM sales_return_lines x JOIN sales_returns r ON r.id=x.sales_return_id
         WHERE r.sales_order_id=sale.id AND r.status='CONFIRMED' AND x.product_id=l.product_id),0)>l.sold_quantity
      OR NOT EXISTS(SELECT 1 FROM inventory_movements m WHERE m.sales_return_id=NEW.id AND m.product_id=l.product_id
        AND m.warehouse_id=NEW.warehouse_id AND m.type='SALE_RETURN' AND m.quantity_change=l.quantity))) THEN
    RAISE EXCEPTION 'Return exceeds quantity or lacks stock movement' USING ERRCODE='23514';
   END IF;
   -- Recompute largest remainder allocation from immutable original lines and verify each official value.
   IF EXISTS (
    WITH shares AS (
      SELECT l.*, CASE WHEN sale.subtotal=0 THEN 0 ELSE div(CAST(l.line_total AS NUMERIC)*sale.discount_amount,sale.subtotal) END base,
       CASE WHEN sale.subtotal=0 THEN 0 ELSE mod(CAST(l.line_total AS NUMERIC)*sale.discount_amount,sale.subtotal) END rem
       FROM sales_order_lines l WHERE l.sales_order_id=sale.id),
    allocations AS (
      SELECT shares.*,base+CASE WHEN row_number() OVER(ORDER BY rem DESC,product_id::text)<=(sale.discount_amount-sum(base) OVER()) THEN 1 ELSE 0 END discount FROM shares),
    values_to_check AS (
      SELECT x.*,a.discount,a.line_total,COALESCE((SELECT sum(CAST(l.quantity AS NUMERIC)) FROM sales_return_lines l JOIN sales_returns r ON r.id=l.sales_return_id
       WHERE r.sales_order_id=sale.id AND r.status='CONFIRMED' AND l.product_id=x.product_id),0) previous
      FROM sales_return_lines x JOIN allocations a ON a.product_id=x.product_id WHERE x.sales_return_id=NEW.id)
    SELECT 1 FROM values_to_check x WHERE x.allocated_discount_amount<>x.discount OR x.net_line_amount<>x.line_total-x.discount
      OR x.return_amount<>div(CAST(x.net_line_amount AS NUMERIC)*(x.previous+x.quantity),x.sold_quantity)-div(CAST(x.net_line_amount AS NUMERIC)*x.previous,x.sold_quantity)
   ) THEN RAISE EXCEPTION 'Official return valuation differs from original allocation' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER sales_returns_history_guard BEFORE INSERT OR UPDATE OR DELETE ON sales_returns FOR EACH ROW EXECUTE FUNCTION protect_sales_return_history();

CREATE FUNCTION protect_refund_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sale sales_orders%ROWTYPE; available NUMERIC;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Refunds cannot be deleted' USING ERRCODE='23514'; END IF;
 SELECT * INTO sale FROM sales_orders WHERE id=NEW.sales_order_id FOR UPDATE;
 IF TG_OP='INSERT' THEN
  SELECT refundable_amount INTO available FROM sales_payment_totals WHERE id=sale.id;
  IF sale.status<>'CONFIRMED' OR NEW.status<>'ACTIVE' OR NEW.amount>available
   OR NEW.warehouse_id IS DISTINCT FROM sale.warehouse_id OR NEW.customer_id IS DISTINCT FROM sale.customer_id THEN
   RAISE EXCEPTION 'Invalid refund or refund exceeds available amount' USING ERRCODE='23514';
  END IF;
 ELSE
  IF OLD.status<>'ACTIVE' OR NEW.status<>'CANCELLED' OR
   (to_jsonb(NEW)-ARRAY['status','cancelled_by','cancelled_at','cancellation_reason'])
    IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','cancelled_by','cancelled_at','cancellation_reason']) THEN
   RAISE EXCEPTION 'Refund history is immutable except cancellation' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER refunds_history_guard BEFORE INSERT OR UPDATE OR DELETE ON refunds FOR EACH ROW EXECUTE FUNCTION protect_refund_history();

CREATE OR REPLACE FUNCTION protect_payment_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sale sales_orders%ROWTYPE; totals RECORD;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payments cannot be deleted' USING ERRCODE='23514'; END IF;
 SELECT * INTO sale FROM sales_orders WHERE id=NEW.sales_order_id FOR UPDATE;
 SELECT * INTO totals FROM sales_payment_totals WHERE id=sale.id;
 IF TG_OP='INSERT' THEN
  IF sale.status<>'CONFIRMED' OR NEW.status<>'ACTIVE' OR NEW.amount>totals.remaining_amount
   OR NEW.warehouse_id IS DISTINCT FROM sale.warehouse_id OR NEW.customer_id IS DISTINCT FROM sale.customer_id THEN
   RAISE EXCEPTION 'Invalid payment source or exceeds adjusted remaining' USING ERRCODE='23514';
  END IF;
 ELSE
  IF OLD.status<>'ACTIVE' OR NEW.status<>'CANCELLED' OR
   (to_jsonb(NEW)-ARRAY['status','cancelled_by','cancelled_at','cancellation_reason'])
    IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','cancelled_by','cancelled_at','cancellation_reason']) THEN
   RAISE EXCEPTION 'Payment history is immutable except cancellation' USING ERRCODE='23514';
  END IF;
  IF totals.paid_amount-OLD.amount<totals.refunded_amount THEN
   RAISE EXCEPTION 'Payment cancellation would undercut active refunds' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION prevent_paid_sale_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='CANCELLED' THEN
  IF EXISTS(SELECT 1 FROM sales_returns WHERE sales_order_id=OLD.id AND status='CONFIRMED') THEN
   RAISE EXCEPTION 'SALES_ORDER_HAS_RETURNS' USING ERRCODE='23514';
  END IF;
  IF EXISTS(SELECT 1 FROM payments WHERE sales_order_id=OLD.id AND status='ACTIVE') THEN
   RAISE EXCEPTION 'Sales with active payments cannot be cancelled' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END;
$$;
