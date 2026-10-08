CREATE TABLE stock_receipts (
 id UUID PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE,
 warehouse_id UUID NOT NULL REFERENCES warehouses(id),
 receipt_date DATE NOT NULL,
 supplier_name VARCHAR(160), note VARCHAR(2000),
 status VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
 total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 confirmed_by UUID REFERENCES users(id), confirmed_at TIMESTAMP WITH TIME ZONE,
 cancelled_by UUID REFERENCES users(id), cancelled_at TIMESTAMP WITH TIME ZONE,
 cancellation_reason VARCHAR(2000),
 CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL)),
 CHECK ((cancelled_by IS NULL) = (cancelled_at IS NULL)),
 CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
 CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL),
 CHECK (status <> 'DRAFT' OR (confirmed_at IS NULL AND cancelled_at IS NULL)),
 CHECK (status <> 'CANCELLED' OR confirmed_at IS NULL OR length(trim(cancellation_reason)) > 0)
);
CREATE INDEX ix_stock_receipts_scope ON stock_receipts(warehouse_id, created_at DESC, id DESC);
CREATE TABLE stock_receipt_lines (
 receipt_id UUID NOT NULL REFERENCES stock_receipts(id),
 product_id UUID NOT NULL REFERENCES products(id),
 quantity BIGINT NOT NULL CHECK (quantity > 0),
 unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
 line_total BIGINT NOT NULL CHECK (line_total >= 0),
 product_code VARCHAR(80) NOT NULL, product_name VARCHAR(160) NOT NULL, unit VARCHAR(10) NOT NULL,
 PRIMARY KEY (receipt_id, product_id),
 CHECK (CAST(quantity AS NUMERIC(38,0)) * CAST(unit_price AS NUMERIC(38,0)) = line_total)
);
CREATE TABLE inventory_balances (
 warehouse_id UUID NOT NULL REFERENCES warehouses(id),
 product_id UUID NOT NULL REFERENCES products(id),
 quantity BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
 PRIMARY KEY (warehouse_id, product_id)
);
CREATE TABLE inventory_movements (
 id UUID PRIMARY KEY,
 warehouse_id UUID NOT NULL REFERENCES warehouses(id),
 product_id UUID NOT NULL REFERENCES products(id),
 quantity_change BIGINT NOT NULL CHECK (quantity_change <> 0),
 type VARCHAR(30) NOT NULL CHECK (type IN ('RECEIPT_CONFIRM','RECEIPT_CANCEL')),
 receipt_id UUID NOT NULL,
 performed_by UUID NOT NULL REFERENCES users(id),
 performed_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE (receipt_id, product_id, type),
 FOREIGN KEY (receipt_id, product_id) REFERENCES stock_receipt_lines(receipt_id, product_id),
 CHECK ((type = 'RECEIPT_CONFIRM' AND quantity_change > 0) OR (type = 'RECEIPT_CANCEL' AND quantity_change < 0))
);
CREATE INDEX ix_inventory_movements_scope ON inventory_movements(warehouse_id, performed_at DESC, id DESC);
CREATE INDEX ix_inventory_movements_product ON inventory_movements(warehouse_id, product_id, performed_at DESC);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000031', 'STOCK_RECEIPT_VIEW', 'STOCK_RECEIPT_VIEW', 'STOCK_RECEIPT_VIEW', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000032', 'STOCK_RECEIPT_CREATE', 'STOCK_RECEIPT_CREATE', 'STOCK_RECEIPT_CREATE', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000033', 'STOCK_RECEIPT_UPDATE', 'STOCK_RECEIPT_UPDATE', 'STOCK_RECEIPT_UPDATE', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000034', 'STOCK_RECEIPT_CONFIRM', 'STOCK_RECEIPT_CONFIRM', 'STOCK_RECEIPT_CONFIRM', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000035', 'STOCK_RECEIPT_CANCEL', 'STOCK_RECEIPT_CANCEL', 'STOCK_RECEIPT_CANCEL', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000036', 'INVENTORY_VIEW', 'INVENTORY_VIEW', 'INVENTORY_VIEW', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000037', 'INVENTORY_MOVEMENT_VIEW', 'INVENTORY_MOVEMENT_VIEW', 'INVENTORY_MOVEMENT_VIEW', 1);
INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN' AND p.code IN ('STOCK_RECEIPT_VIEW','STOCK_RECEIPT_CREATE','STOCK_RECEIPT_UPDATE','STOCK_RECEIPT_CONFIRM','STOCK_RECEIPT_CANCEL','INVENTORY_VIEW','INVENTORY_MOVEMENT_VIEW');
