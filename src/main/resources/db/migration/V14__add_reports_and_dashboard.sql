-- Seed only; do not grant these permissions to roles/users. ADMIN uses the existing exception.
INSERT INTO permissions(id,name,code,description,status) VALUES
 ('20000000-0000-0000-0000-000000000055','REPORT_INVENTORY_VIEW','REPORT_INVENTORY_VIEW','Historical inventory report',1),
 ('20000000-0000-0000-0000-000000000056','REPORT_SALES_VIEW','REPORT_SALES_VIEW','Event-based sales report',1),
 ('20000000-0000-0000-0000-000000000057','REPORT_PAYMENT_VIEW','REPORT_PAYMENT_VIEW','System-time payment record report',1),
 ('20000000-0000-0000-0000-000000000058','REPORT_RECEIVABLE_VIEW','REPORT_RECEIVABLE_VIEW','Current receivables summary',1),
 ('20000000-0000-0000-0000-000000000059','REPORT_LOW_STOCK_VIEW','REPORT_LOW_STOCK_VIEW','Current low stock report',1),
 ('20000000-0000-0000-0000-000000000060','REPORT_DASHBOARD_VIEW','REPORT_DASHBOARD_VIEW','Permission-filtered dashboard',1);
CREATE INDEX ix_report_sales_confirmed ON sales_orders(warehouse_id,confirmed_at);
CREATE INDEX ix_report_sales_cancelled ON sales_orders(warehouse_id,cancelled_at);
CREATE INDEX ix_report_payments_created ON payments(warehouse_id,created_at);
CREATE INDEX ix_report_payments_cancelled ON payments(warehouse_id,cancelled_at);
CREATE INDEX ix_report_sales_outstanding ON sales_orders(warehouse_id,status,customer_id);
CREATE INDEX ix_report_products_low_stock ON products(status,low_stock_threshold,id);
-- Movement warehouse/product/time and payment order/status indexes already exist.
