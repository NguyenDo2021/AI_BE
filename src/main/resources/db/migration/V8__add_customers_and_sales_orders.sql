CREATE TABLE customers (
 id UUID PRIMARY KEY, warehouse_id UUID NOT NULL REFERENCES warehouses(id),
 code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
 phone VARCHAR(20), address VARCHAR(500), note VARCHAR(2000),
 status SMALLINT NOT NULL CHECK (status IN (0,1)),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE (id,warehouse_id)
);
CREATE INDEX ix_customers_scope ON customers(warehouse_id,created_at DESC,id DESC);
CREATE TABLE sales_orders (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 warehouse_id UUID NOT NULL REFERENCES warehouses(id), customer_id UUID,
 sale_date DATE NOT NULL, note VARCHAR(2000),
 status VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
 subtotal BIGINT NOT NULL CHECK (subtotal >= 0),
 discount_amount BIGINT NOT NULL DEFAULT 0 CHECK (discount_amount >= 0 AND discount_amount <= subtotal),
 total_amount BIGINT NOT NULL CHECK (total_amount >= 0 AND total_amount = subtotal-discount_amount),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 customer_code VARCHAR(40), customer_name VARCHAR(160), customer_phone VARCHAR(20), customer_address VARCHAR(500),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 confirmed_by UUID REFERENCES users(id), confirmed_at TIMESTAMP WITH TIME ZONE,
 cancelled_by UUID REFERENCES users(id), cancelled_at TIMESTAMP WITH TIME ZONE,
 cancellation_reason VARCHAR(2000), goods_returned BOOLEAN,
 FOREIGN KEY (customer_id,warehouse_id) REFERENCES customers(id,warehouse_id),
 CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL)),
 CHECK ((cancelled_by IS NULL) = (cancelled_at IS NULL)),
 CHECK (status <> 'CONFIRMED' OR (confirmed_at IS NOT NULL AND customer_name IS NOT NULL)),
 CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason)) > 0)),
 CHECK (status <> 'DRAFT' OR (confirmed_at IS NULL AND cancelled_at IS NULL)),
 CHECK (status <> 'CANCELLED' OR confirmed_at IS NULL OR (goods_returned IS NOT NULL AND goods_returned = TRUE))
);
CREATE INDEX ix_sales_orders_scope ON sales_orders(warehouse_id,sale_date,created_at DESC,id DESC);
CREATE INDEX ix_sales_orders_customer ON sales_orders(customer_id,sale_date);
CREATE TABLE sales_order_lines (
 sales_order_id UUID NOT NULL REFERENCES sales_orders(id), product_id UUID NOT NULL REFERENCES products(id),
 quantity BIGINT NOT NULL CHECK (quantity > 0), unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
 line_total BIGINT NOT NULL CHECK (line_total >= 0),
 product_code VARCHAR(80) NOT NULL, product_name VARCHAR(160) NOT NULL, unit VARCHAR(10) NOT NULL,
 PRIMARY KEY (sales_order_id,product_id),
 CHECK (CAST(quantity AS NUMERIC(38,0)) * CAST(unit_price AS NUMERIC(38,0)) = line_total)
);
ALTER TABLE inventory_movements ALTER COLUMN receipt_id DROP NOT NULL;
ALTER TABLE inventory_movements ADD COLUMN sales_order_id UUID;
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_type_check;
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_check;
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_type_check CHECK (type IN ('RECEIPT_CONFIRM','RECEIPT_CANCEL','SALE_CONFIRM','SALE_CANCEL'));
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_source_sign_check CHECK (
 (type='RECEIPT_CONFIRM' AND receipt_id IS NOT NULL AND sales_order_id IS NULL AND quantity_change>0) OR
 (type='RECEIPT_CANCEL' AND receipt_id IS NOT NULL AND sales_order_id IS NULL AND quantity_change<0) OR
 (type='SALE_CONFIRM' AND sales_order_id IS NOT NULL AND receipt_id IS NULL AND quantity_change<0) OR
 (type='SALE_CANCEL' AND sales_order_id IS NOT NULL AND receipt_id IS NULL AND quantity_change>0)
);
ALTER TABLE inventory_movements ADD CONSTRAINT fk_inventory_sale_line FOREIGN KEY (sales_order_id,product_id) REFERENCES sales_order_lines(sales_order_id,product_id);
ALTER TABLE inventory_movements ADD CONSTRAINT uq_inventory_sale_movement UNIQUE (sales_order_id,product_id,type);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000038', 'CUSTOMER_VIEW', 'CUSTOMER_VIEW', 'CUSTOMER_VIEW', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000039', 'CUSTOMER_CREATE', 'CUSTOMER_CREATE', 'CUSTOMER_CREATE', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000040', 'CUSTOMER_UPDATE', 'CUSTOMER_UPDATE', 'CUSTOMER_UPDATE', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000041', 'SALES_ORDER_VIEW', 'SALES_ORDER_VIEW', 'SALES_ORDER_VIEW', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000042', 'SALES_ORDER_CREATE', 'SALES_ORDER_CREATE', 'SALES_ORDER_CREATE', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000043', 'SALES_ORDER_UPDATE', 'SALES_ORDER_UPDATE', 'SALES_ORDER_UPDATE', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000044', 'SALES_ORDER_CONFIRM', 'SALES_ORDER_CONFIRM', 'SALES_ORDER_CONFIRM', 1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000045', 'SALES_ORDER_CANCEL', 'SALES_ORDER_CANCEL', 'SALES_ORDER_CANCEL', 1);
INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code IN ('CUSTOMER_VIEW','CUSTOMER_CREATE','CUSTOMER_UPDATE','SALES_ORDER_VIEW','SALES_ORDER_CREATE','SALES_ORDER_UPDATE','SALES_ORDER_CONFIRM','SALES_ORDER_CANCEL');
