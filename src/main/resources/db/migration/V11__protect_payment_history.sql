CREATE FUNCTION protect_payment_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sale sales_orders%ROWTYPE; paid NUMERIC;
BEGIN
 IF TG_OP='DELETE' THEN
  RAISE EXCEPTION 'Payments cannot be deleted' USING ERRCODE='23514';
 END IF;
 -- The application always locks warehouse -> sale before reaching this guard.
 SELECT * INTO sale FROM sales_orders WHERE id=NEW.sales_order_id FOR UPDATE;
 IF TG_OP='INSERT' THEN
  IF sale.status<>'CONFIRMED' OR NEW.status<>'ACTIVE'
   OR NEW.warehouse_id IS DISTINCT FROM sale.warehouse_id
   OR NEW.customer_id IS DISTINCT FROM sale.customer_id THEN
   RAISE EXCEPTION 'Invalid payment source or state' USING ERRCODE='23514';
  END IF;
  SELECT COALESCE(SUM(CAST(amount AS NUMERIC)),0) INTO paid FROM payments
   WHERE sales_order_id=NEW.sales_order_id AND status='ACTIVE';
  IF paid+NEW.amount > sale.total_amount THEN
   RAISE EXCEPTION 'Payment exceeds remaining amount' USING ERRCODE='23514';
  END IF;
 ELSE
  IF OLD.status<>'ACTIVE' OR NEW.status<>'CANCELLED'
   OR NEW.id IS DISTINCT FROM OLD.id OR NEW.code IS DISTINCT FROM OLD.code
   OR NEW.sales_order_id IS DISTINCT FROM OLD.sales_order_id
   OR NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
   OR NEW.amount IS DISTINCT FROM OLD.amount OR NEW.payment_date IS DISTINCT FROM OLD.payment_date
   OR NEW.method IS DISTINCT FROM OLD.method OR NEW.reference IS DISTINCT FROM OLD.reference
   OR NEW.note IS DISTINCT FROM OLD.note OR NEW.created_by IS DISTINCT FROM OLD.created_by
   OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key THEN
   RAISE EXCEPTION 'Payment history is immutable except cancellation' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER payments_history_guard BEFORE INSERT OR UPDATE OR DELETE ON payments
 FOR EACH ROW EXECUTE FUNCTION protect_payment_history();
CREATE FUNCTION prevent_paid_sale_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='CANCELLED' AND EXISTS
 (SELECT 1 FROM payments WHERE sales_order_id=OLD.id AND status='ACTIVE') THEN
  RAISE EXCEPTION 'Paid sales cannot be cancelled; refund is not supported' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER sales_orders_payment_guard BEFORE UPDATE ON sales_orders
 FOR EACH ROW EXECUTE FUNCTION prevent_paid_sale_cancellation();
