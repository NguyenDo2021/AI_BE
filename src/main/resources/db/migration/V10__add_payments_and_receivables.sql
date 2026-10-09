CREATE TABLE payments (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 sales_order_id UUID NOT NULL REFERENCES sales_orders(id),
 warehouse_id UUID NOT NULL REFERENCES warehouses(id), customer_id UUID,
 amount BIGINT NOT NULL CHECK (amount > 0), payment_date DATE NOT NULL,
 method VARCHAR(20) NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER')),
 reference VARCHAR(200), note VARCHAR(2000),
 status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','CANCELLED')),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 idempotency_key UUID NOT NULL,
 cancelled_by UUID REFERENCES users(id), cancelled_at TIMESTAMP WITH TIME ZONE,
 cancellation_reason VARCHAR(2000),
 UNIQUE (created_by,idempotency_key),
 FOREIGN KEY (customer_id,warehouse_id) REFERENCES customers(id,warehouse_id),
 CHECK ((status='ACTIVE' AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancellation_reason IS NULL)
 OR (status='CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL
 AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason)) > 0))
);
CREATE INDEX ix_payments_order ON payments(sales_order_id,status);
CREATE INDEX ix_payments_scope ON payments(warehouse_id,payment_date,created_at DESC,id DESC);
CREATE INDEX ix_payments_customer ON payments(customer_id,payment_date);
CREATE VIEW sales_payment_totals AS
 SELECT s.id, s.warehouse_id, s.customer_id, s.status, s.total_amount,
 COALESCE(p.paid_amount,0) AS paid_amount,
 CASE WHEN s.status='CONFIRMED' THEN s.total_amount-COALESCE(p.paid_amount,0) ELSE 0 END AS remaining_amount
 FROM sales_orders s LEFT JOIN
 (SELECT sales_order_id,SUM(CAST(amount AS NUMERIC(38,0))) AS paid_amount FROM payments
 WHERE status='ACTIVE' GROUP BY sales_order_id) p ON p.sales_order_id=s.id;
INSERT INTO permissions(id,name,code,description,status) VALUES
 ('20000000-0000-0000-0000-000000000046','PAYMENT_VIEW','PAYMENT_VIEW','View payment history',1),
 ('20000000-0000-0000-0000-000000000047','PAYMENT_CREATE','PAYMENT_CREATE','Record payment',1),
 ('20000000-0000-0000-0000-000000000048','PAYMENT_CANCEL','PAYMENT_CANCEL','Reverse incorrect payment record',1),
 ('20000000-0000-0000-0000-000000000049','RECEIVABLE_VIEW','RECEIVABLE_VIEW','View current receivables',1);
-- No role/user grants: ADMIN uses the existing live role exception.
