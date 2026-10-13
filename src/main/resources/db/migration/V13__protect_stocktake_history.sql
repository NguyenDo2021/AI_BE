CREATE FUNCTION protect_stocktake_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'Stocktakes cannot be deleted' USING ERRCODE = '23514';
 END IF;
 IF OLD.status <> 'DRAFT' THEN
  RAISE EXCEPTION 'Final stocktake is immutable' USING ERRCODE = '23514';
 END IF;
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id
  OR NEW.code IS DISTINCT FROM OLD.code OR NEW.created_by IS DISTINCT FROM OLD.created_by
  OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
  RAISE EXCEPTION 'Stocktake identity is immutable' USING ERRCODE = '23514';
 END IF;
 IF NEW.version <> OLD.version + 1 THEN
  RAISE EXCEPTION 'Stocktake version must increment by one' USING ERRCODE = '23514';
 END IF;
 IF NEW.status = 'CONFIRMED' AND (
  (SELECT count(*) FROM stocktake_lines WHERE stocktake_id=NEW.id) NOT BETWEEN 1 AND 1000
  OR EXISTS(SELECT 1 FROM stocktake_lines WHERE stocktake_id=NEW.id AND counted_quantity IS NULL)) THEN
  RAISE EXCEPTION 'Confirmed stocktake must have counted lines' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER stocktakes_state_guard BEFORE UPDATE OR DELETE ON stocktakes
 FOR EACH ROW EXECUTE FUNCTION protect_stocktake_state();
CREATE FUNCTION protect_stocktake_lines() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE document_state VARCHAR(10);
BEGIN
 SELECT status INTO document_state FROM stocktakes
 WHERE id=CASE WHEN TG_OP='INSERT' THEN NEW.stocktake_id ELSE OLD.stocktake_id END FOR UPDATE;
 IF document_state <> 'DRAFT' THEN
  RAISE EXCEPTION 'Final stocktake lines are immutable' USING ERRCODE = '23514';
 END IF;
 IF TG_OP='UPDATE' AND NEW.stocktake_id IS DISTINCT FROM OLD.stocktake_id THEN
  RAISE EXCEPTION 'Stocktake line parent is immutable' USING ERRCODE = '23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER stocktake_lines_immutable BEFORE INSERT OR UPDATE OR DELETE ON stocktake_lines
 FOR EACH ROW EXECUTE FUNCTION protect_stocktake_lines();
