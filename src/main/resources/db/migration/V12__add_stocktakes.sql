ALTER TABLE inventory_balances ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0);
CREATE TABLE stocktakes (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 warehouse_id UUID NOT NULL REFERENCES warehouses(id), note VARCHAR(2000),
 status VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_by UUID NOT NULL REFERENCES users(id), updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 confirmed_by UUID REFERENCES users(id), confirmed_at TIMESTAMP WITH TIME ZONE,
 cancelled_by UUID REFERENCES users(id), cancelled_at TIMESTAMP WITH TIME ZONE, cancellation_reason VARCHAR(2000),
 CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL)),
 CHECK ((cancelled_by IS NULL) = (cancelled_at IS NULL)),
 CHECK (status <> 'DRAFT' OR (confirmed_at IS NULL AND cancelled_at IS NULL)),
 CHECK (status <> 'CONFIRMED' OR (confirmed_at IS NOT NULL AND cancelled_at IS NULL)),
 CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND confirmed_at IS NULL AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason)) > 0))
);
CREATE INDEX ix_stocktakes_scope ON stocktakes(warehouse_id,created_at DESC,id DESC);
CREATE TABLE stocktake_lines (
 stocktake_id UUID NOT NULL REFERENCES stocktakes(id), product_id UUID NOT NULL REFERENCES products(id),
 system_quantity BIGINT NOT NULL CHECK (system_quantity >= 0),
 inventory_revision BIGINT NOT NULL CHECK (inventory_revision >= 0),
 counted_quantity BIGINT CHECK (counted_quantity >= 0), reason VARCHAR(2000),
 product_code VARCHAR(80) NOT NULL, product_name VARCHAR(160) NOT NULL, unit VARCHAR(10) NOT NULL,
 PRIMARY KEY(stocktake_id,product_id),
 CHECK (counted_quantity IS NULL OR counted_quantity = system_quantity OR (reason IS NOT NULL AND length(trim(reason)) > 0))
);
ALTER TABLE inventory_movements ADD COLUMN stocktake_id UUID;
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_type_check;
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_source_sign_check;
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_type_check CHECK (type IN ('RECEIPT_CONFIRM','RECEIPT_CANCEL','SALE_CONFIRM','SALE_CANCEL','STOCKTAKE_ADJUSTMENT'));
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_source_sign_check CHECK (
 (type='RECEIPT_CONFIRM' AND receipt_id IS NOT NULL AND sales_order_id IS NULL AND stocktake_id IS NULL AND quantity_change>0) OR
 (type='RECEIPT_CANCEL' AND receipt_id IS NOT NULL AND sales_order_id IS NULL AND stocktake_id IS NULL AND quantity_change<0) OR
 (type='SALE_CONFIRM' AND sales_order_id IS NOT NULL AND receipt_id IS NULL AND stocktake_id IS NULL AND quantity_change<0) OR
 (type='SALE_CANCEL' AND sales_order_id IS NOT NULL AND receipt_id IS NULL AND stocktake_id IS NULL AND quantity_change>0) OR
 (type='STOCKTAKE_ADJUSTMENT' AND stocktake_id IS NOT NULL AND receipt_id IS NULL AND sales_order_id IS NULL AND quantity_change<>0)
);
ALTER TABLE inventory_movements ADD CONSTRAINT fk_inventory_stocktake_line FOREIGN KEY (stocktake_id,product_id) REFERENCES stocktake_lines(stocktake_id,product_id);
ALTER TABLE inventory_movements ADD CONSTRAINT uq_inventory_stocktake_movement UNIQUE(stocktake_id,product_id,type);
INSERT INTO permissions(id,name,code,description,status) VALUES
 ('20000000-0000-0000-0000-000000000050','STOCKTAKE_VIEW','STOCKTAKE_VIEW','View stocktakes',1),
 ('20000000-0000-0000-0000-000000000051','STOCKTAKE_CREATE','STOCKTAKE_CREATE','Create stocktakes',1),
 ('20000000-0000-0000-0000-000000000052','STOCKTAKE_UPDATE','STOCKTAKE_UPDATE','Update stocktakes and refresh snapshots',1),
 ('20000000-0000-0000-0000-000000000053','STOCKTAKE_CONFIRM','STOCKTAKE_CONFIRM','Confirm stocktakes',1),
 ('20000000-0000-0000-0000-000000000054','STOCKTAKE_CANCEL','STOCKTAKE_CANCEL','Cancel draft stocktakes',1);
-- Deliberately no automatic role/user permission assignment.
