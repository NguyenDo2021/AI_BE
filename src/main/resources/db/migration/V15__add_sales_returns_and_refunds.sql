CREATE TABLE sales_returns (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 sales_order_id UUID NOT NULL REFERENCES sales_orders(id),
 warehouse_id UUID NOT NULL REFERENCES warehouses(id), customer_id UUID,
 return_date DATE NOT NULL, reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0), note VARCHAR(2000),
 status VARCHAR(10) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED')),
 total_amount BIGINT NOT NULL CHECK(total_amount>=0), version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_by UUID NOT NULL REFERENCES users(id), updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 confirmed_by UUID REFERENCES users(id), confirmed_at TIMESTAMP WITH TIME ZONE,
 cancelled_by UUID REFERENCES users(id), cancelled_at TIMESTAMP WITH TIME ZONE, cancellation_reason VARCHAR(2000),
 FOREIGN KEY(customer_id,warehouse_id) REFERENCES customers(id,warehouse_id),
 CHECK((status='DRAFT' AND confirmed_at IS NULL AND confirmed_by IS NULL AND cancelled_at IS NULL AND cancelled_by IS NULL)
 OR (status='CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL AND cancelled_at IS NULL AND cancelled_by IS NULL)
 OR (status='CANCELLED' AND confirmed_at IS NULL AND confirmed_by IS NULL AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason))>0))
);
CREATE TABLE sales_return_lines (
 sales_return_id UUID NOT NULL REFERENCES sales_returns(id), product_id UUID NOT NULL REFERENCES products(id),
 quantity BIGINT NOT NULL CHECK(quantity>0), return_amount BIGINT NOT NULL CHECK(return_amount>=0),
 sold_quantity BIGINT NOT NULL CHECK(sold_quantity>0),
 allocated_discount_amount BIGINT NOT NULL CHECK(allocated_discount_amount>=0),
 net_line_amount BIGINT NOT NULL CHECK(net_line_amount>=0),
 product_code VARCHAR(80) NOT NULL, product_name VARCHAR(160) NOT NULL, unit VARCHAR(10) NOT NULL,
 PRIMARY KEY(sales_return_id,product_id), CHECK(quantity<=sold_quantity)
);
CREATE TABLE refunds (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 sales_order_id UUID NOT NULL REFERENCES sales_orders(id),
 warehouse_id UUID NOT NULL REFERENCES warehouses(id), customer_id UUID,
 amount BIGINT NOT NULL CHECK(amount>0), refund_date DATE NOT NULL,
 method VARCHAR(20) NOT NULL CHECK(method IN ('CASH','BANK_TRANSFER')), reference VARCHAR(200),
 reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0), note VARCHAR(2000),
 status VARCHAR(10) NOT NULL CHECK(status IN ('ACTIVE','CANCELLED')),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 idempotency_key UUID NOT NULL, UNIQUE(created_by,idempotency_key),
 cancelled_by UUID REFERENCES users(id), cancelled_at TIMESTAMP WITH TIME ZONE, cancellation_reason VARCHAR(2000),
 FOREIGN KEY(customer_id,warehouse_id) REFERENCES customers(id,warehouse_id),
 CHECK((status='ACTIVE' AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancellation_reason IS NULL)
 OR (status='CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason))>0))
);
CREATE INDEX ix_sales_returns_order ON sales_returns(sales_order_id,status);
CREATE INDEX ix_sales_returns_scope ON sales_returns(warehouse_id,return_date,created_at DESC,id DESC);
CREATE INDEX ix_sales_returns_confirmed ON sales_returns(warehouse_id,confirmed_at);
CREATE INDEX ix_refunds_order ON refunds(sales_order_id,status);
CREATE INDEX ix_refunds_scope ON refunds(warehouse_id,refund_date,created_at DESC,id DESC);
CREATE INDEX ix_refunds_created ON refunds(warehouse_id,created_at);
CREATE INDEX ix_refunds_cancelled ON refunds(warehouse_id,cancelled_at);
ALTER TABLE inventory_movements ADD COLUMN sales_return_id UUID;
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_type_check;
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_source_sign_check;
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_type_check CHECK(type IN ('RECEIPT_CONFIRM','RECEIPT_CANCEL','SALE_CONFIRM','SALE_CANCEL','STOCKTAKE_ADJUSTMENT','SALE_RETURN'));
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_source_sign_check CHECK (
 (type='SALE_RETURN' AND sales_return_id IS NOT NULL AND sales_order_id IS NULL AND receipt_id IS NULL AND stocktake_id IS NULL AND quantity_change>0)
 OR (sales_return_id IS NULL AND (
 (type='RECEIPT_CONFIRM' AND receipt_id IS NOT NULL AND sales_order_id IS NULL AND stocktake_id IS NULL AND quantity_change>0) OR
 (type='RECEIPT_CANCEL' AND receipt_id IS NOT NULL AND sales_order_id IS NULL AND stocktake_id IS NULL AND quantity_change<0) OR
 (type='SALE_CONFIRM' AND sales_order_id IS NOT NULL AND receipt_id IS NULL AND stocktake_id IS NULL AND quantity_change<0) OR
 (type='SALE_CANCEL' AND sales_order_id IS NOT NULL AND receipt_id IS NULL AND stocktake_id IS NULL AND quantity_change>0) OR
 (type='STOCKTAKE_ADJUSTMENT' AND stocktake_id IS NOT NULL AND receipt_id IS NULL AND sales_order_id IS NULL AND quantity_change<>0)))
);
ALTER TABLE inventory_movements ADD CONSTRAINT fk_inventory_return_line FOREIGN KEY(sales_return_id,product_id) REFERENCES sales_return_lines(sales_return_id,product_id);
ALTER TABLE inventory_movements ADD CONSTRAINT uq_inventory_return_movement UNIQUE(sales_return_id,product_id,type);
CREATE OR REPLACE VIEW sales_payment_totals AS
 SELECT s.id,s.warehouse_id,s.customer_id,s.status,s.total_amount,
 COALESCE(p.paid_amount,0) paid_amount,
 CASE WHEN s.status='CONFIRMED' THEN GREATEST(s.total_amount-COALESCE(r.returned_amount,0)-COALESCE(p.paid_amount,0)+COALESCE(f.refunded_amount,0),0) ELSE 0 END remaining_amount,
 COALESCE(r.returned_amount,0) returned_amount,
 s.total_amount-COALESCE(r.returned_amount,0) adjusted_total_amount,
 COALESCE(f.refunded_amount,0) refunded_amount,
 COALESCE(p.paid_amount,0)-COALESCE(f.refunded_amount,0) net_paid_amount,
 CASE WHEN s.status='CONFIRMED' THEN GREATEST(COALESCE(p.paid_amount,0)-COALESCE(f.refunded_amount,0)-s.total_amount+COALESCE(r.returned_amount,0),0) ELSE 0 END refundable_amount
 FROM sales_orders s
 LEFT JOIN (SELECT sales_order_id,SUM(CAST(amount AS NUMERIC(38,0))) paid_amount FROM payments WHERE status='ACTIVE' GROUP BY sales_order_id) p ON p.sales_order_id=s.id
 LEFT JOIN (SELECT sales_order_id,SUM(CAST(total_amount AS NUMERIC(38,0))) returned_amount FROM sales_returns WHERE status='CONFIRMED' GROUP BY sales_order_id) r ON r.sales_order_id=s.id
 LEFT JOIN (SELECT sales_order_id,SUM(CAST(amount AS NUMERIC(38,0))) refunded_amount FROM refunds WHERE status='ACTIVE' GROUP BY sales_order_id) f ON f.sales_order_id=s.id;
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000061','SALES_RETURN_VIEW','SALES_RETURN_VIEW','SALES_RETURN_VIEW',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000062','SALES_RETURN_CREATE','SALES_RETURN_CREATE','SALES_RETURN_CREATE',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000063','SALES_RETURN_UPDATE','SALES_RETURN_UPDATE','SALES_RETURN_UPDATE',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000064','SALES_RETURN_CONFIRM','SALES_RETURN_CONFIRM','SALES_RETURN_CONFIRM',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000065','SALES_RETURN_CANCEL','SALES_RETURN_CANCEL','SALES_RETURN_CANCEL',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000066','REFUND_VIEW','REFUND_VIEW','REFUND_VIEW',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000067','REFUND_CREATE','REFUND_CREATE','REFUND_CREATE',1);
INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000068','REFUND_CANCEL','REFUND_CANCEL','REFUND_CANCEL',1);
-- Seed only; no automatic grants.
