-- PostgreSQL defense in depth: movement audit is append-only; final receipt lines are frozen.
CREATE FUNCTION reject_inventory_movement_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Inventory movements are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER inventory_movements_immutable BEFORE UPDATE OR DELETE ON inventory_movements
FOR EACH ROW EXECUTE FUNCTION reject_inventory_movement_mutation();

CREATE FUNCTION protect_stock_receipt_lines() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE receipt_state VARCHAR(10);
BEGIN
    SELECT status INTO receipt_state FROM stock_receipts
    WHERE id = CASE WHEN TG_OP = 'INSERT' THEN NEW.receipt_id ELSE OLD.receipt_id END FOR UPDATE;
    IF receipt_state <> 'DRAFT' THEN
        RAISE EXCEPTION 'Final receipt lines are immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.receipt_id IS DISTINCT FROM OLD.receipt_id THEN
        RAISE EXCEPTION 'Receipt line parent is immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER stock_receipt_lines_immutable BEFORE INSERT OR UPDATE OR DELETE ON stock_receipt_lines
FOR EACH ROW EXECUTE FUNCTION protect_stock_receipt_lines();

CREATE FUNCTION protect_stock_receipt_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Receipts cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id OR NEW.id IS DISTINCT FROM OLD.id
        OR NEW.code IS DISTINCT FROM OLD.code OR NEW.created_by IS DISTINCT FROM OLD.created_by
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'Receipt identity is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'CANCELLED' THEN
        RAISE EXCEPTION 'Cancelled receipt is final' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'CONFIRMED' AND (NEW.status <> 'CANCELLED'
        OR NEW.receipt_date IS DISTINCT FROM OLD.receipt_date
        OR NEW.supplier_name IS DISTINCT FROM OLD.supplier_name OR NEW.note IS DISTINCT FROM OLD.note
        OR NEW.total_amount IS DISTINCT FROM OLD.total_amount
        OR NEW.confirmed_by IS DISTINCT FROM OLD.confirmed_by OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at) THEN
        RAISE EXCEPTION 'Confirmed receipt can only be cancelled' USING ERRCODE = '23514';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'Receipt version must increment by one' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER stock_receipts_state_guard BEFORE UPDATE OR DELETE ON stock_receipts
FOR EACH ROW EXECUTE FUNCTION protect_stock_receipt_state();
