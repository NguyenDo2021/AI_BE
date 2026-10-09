-- Movement audit remains append-only through the V7 trigger.
CREATE FUNCTION protect_sales_order_lines() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE order_state VARCHAR(10);
BEGIN
 SELECT status INTO order_state FROM sales_orders
 WHERE id = CASE WHEN TG_OP='INSERT' THEN NEW.sales_order_id ELSE OLD.sales_order_id END FOR UPDATE;
 IF order_state <> 'DRAFT' THEN
  RAISE EXCEPTION 'Final sales order lines are immutable' USING ERRCODE='23514';
 END IF;
 IF TG_OP='UPDATE' AND NEW.sales_order_id IS DISTINCT FROM OLD.sales_order_id THEN
  RAISE EXCEPTION 'Sales line parent is immutable' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER sales_order_lines_immutable BEFORE INSERT OR UPDATE OR DELETE ON sales_order_lines
FOR EACH ROW EXECUTE FUNCTION protect_sales_order_lines();
CREATE FUNCTION protect_sales_order_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Sales orders cannot be deleted' USING ERRCODE='23514'; END IF;
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.code IS DISTINCT FROM OLD.code
  OR NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id OR NEW.created_by IS DISTINCT FROM OLD.created_by
  OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
  RAISE EXCEPTION 'Sales identity is immutable' USING ERRCODE='23514';
 END IF;
 IF OLD.status='CANCELLED' THEN RAISE EXCEPTION 'Cancelled sale is final' USING ERRCODE='23514'; END IF;
 IF OLD.status='CONFIRMED' AND (NEW.status<>'CANCELLED'
  OR NEW.customer_id IS DISTINCT FROM OLD.customer_id OR NEW.sale_date IS DISTINCT FROM OLD.sale_date
  OR NEW.note IS DISTINCT FROM OLD.note OR NEW.subtotal IS DISTINCT FROM OLD.subtotal
  OR NEW.discount_amount IS DISTINCT FROM OLD.discount_amount OR NEW.total_amount IS DISTINCT FROM OLD.total_amount
  OR NEW.customer_code IS DISTINCT FROM OLD.customer_code OR NEW.customer_name IS DISTINCT FROM OLD.customer_name
  OR NEW.customer_phone IS DISTINCT FROM OLD.customer_phone OR NEW.customer_address IS DISTINCT FROM OLD.customer_address
  OR NEW.confirmed_by IS DISTINCT FROM OLD.confirmed_by OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at) THEN
  RAISE EXCEPTION 'Confirmed sale can only be cancelled' USING ERRCODE='23514';
 END IF;
 IF NEW.version <> OLD.version+1 THEN RAISE EXCEPTION 'Sale version must increment by one' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER sales_orders_state_guard BEFORE UPDATE OR DELETE ON sales_orders
FOR EACH ROW EXECUTE FUNCTION protect_sales_order_state();
CREATE FUNCTION protect_customer_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.code IS DISTINCT FROM OLD.code
  OR NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
  RAISE EXCEPTION 'Customer identity and warehouse are immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER customers_identity_guard BEFORE UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION protect_customer_identity();
