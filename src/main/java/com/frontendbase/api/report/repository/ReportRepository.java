package com.frontendbase.api.report.repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.frontendbase.api.report.dto.ReportDtos.*;
import com.frontendbase.api.report.service.ReportAccess.Scope;
import com.frontendbase.api.report.service.ReportPeriod;
import com.frontendbase.api.payment.repository.PaymentRepository;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReportRepository {
        private final JdbcTemplate jdbc;
        private final PaymentRepository numbers;

        private long exact(ResultSet r, String column) throws SQLException {
                return numbers.exact(r.getBigDecimal(column));
        }

        private record Query(String sql, List<Object> args, String orderBy) {
        }

        private <T> PageResponse<T> page(Query q, RowMapper<T> mapper, int page, int size) {
                long total = jdbc.queryForObject("SELECT count(*) FROM (" + q.sql + ") report_rows",
                                Long.class, q.args.toArray());
                var args = new ArrayList<>(q.args);
                args.add(size);
                args.add(((long) page - 1) * size);
                var items = jdbc.query(q.sql + " ORDER BY " + q.orderBy
                                + " LIMIT ? OFFSET ?",
                                mapper, args.toArray());
                return new PageResponse<>(items, total, page, size);
        }

        private String product(UUID productId, String column, List<Object> args) {
                if (productId == null)
                        return "";
                args.add(productId);
                return " AND " + column + "=?";
        }

        /**
         * Never invent opening stock when imported/untracked balances disagree with the
         * ledger.
         */
        public void verifyInventoryHistory(Scope scope, UUID productId) {
                var args = new ArrayList<Object>();
                String filter = scope.where("m.warehouse_id", args) + product(productId, "m.product_id", args);
                String ledger = "SELECT m.warehouse_id,m.product_id,SUM(CAST(m.quantity_change AS NUMERIC)) quantity"
                                + " FROM inventory_movements m WHERE 1=1" + filter
                                + " GROUP BY m.warehouse_id,m.product_id";
                String balanceFilter = scope.where("b.warehouse_id", args) + product(productId, "b.product_id", args);
                // UNION keys also detects movements without a balance row.
                String sql = "WITH ledger AS (" + ledger + "), balances AS ("
                                + "SELECT b.warehouse_id,b.product_id,b.quantity FROM inventory_balances b WHERE 1=1"
                                + balanceFilter + "), keys AS (SELECT warehouse_id,product_id FROM ledger UNION"
                                + " SELECT warehouse_id,product_id FROM balances)"
                                + " SELECT count(*) FROM keys k LEFT JOIN ledger l ON l.warehouse_id=k.warehouse_id"
                                + " AND l.product_id=k.product_id LEFT JOIN balances b ON b.warehouse_id=k.warehouse_id"
                                + " AND b.product_id=k.product_id WHERE COALESCE(l.quantity,0)<>COALESCE(b.quantity,0)";
                if (jdbc.queryForObject(sql, Long.class, args.toArray()) > 0)
                        throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_HISTORY_INCONSISTENT",
                                        "Balances differ from movement history; reconcile source documents before reporting historical stock");
        }

        public PageResponse<InventoryRow> inventory(Scope scope, UUID productId, ReportPeriod period, int page,
                        int size) {
                var args = new ArrayList<Object>();
                args.add(period.start());
                StringBuilder select = new StringBuilder(
                                "SELECT m.warehouse_id,w.code warehouse_code,w.name warehouse_name,"
                                                + "m.product_id,p.code product_code,p.name product_name,p.unit,p.status product_status,"
                                                + "COALESCE(SUM(CASE WHEN m.performed_at<? THEN CAST(m.quantity_change AS NUMERIC) ELSE 0 END),0) opening_quantity");
                String[] types = { "RECEIPT_CONFIRM", "RECEIPT_CANCEL", "SALE_CONFIRM", "SALE_CANCEL",
                                "STOCKTAKE_ADJUSTMENT", "STOCKTAKE_ADJUSTMENT" };
                String[] columns = { "receipt_quantity", "receipt_cancellation_quantity", "sale_quantity",
                                "sale_cancellation_quantity", "adjustment_increase_quantity",
                                "adjustment_decrease_quantity" };
                for (int i = 0; i < types.length; i++) {
                        args.add(period.start());
                        String sign = i == 4 ? " AND m.quantity_change>0" : i == 5 ? " AND m.quantity_change<0" : "";
                        select.append(",COALESCE(SUM(CASE WHEN m.performed_at>=? AND m.type='")
                                        .append(types[i]).append("'").append(sign)
                                        .append(" THEN ABS(CAST(m.quantity_change AS NUMERIC)) ELSE 0 END),0) ")
                                        .append(columns[i]);
                }
                select.append(",COALESCE(SUM(CAST(m.quantity_change AS NUMERIC)),0) closing_quantity"
                                + " FROM inventory_movements m JOIN warehouses w ON w.id=m.warehouse_id"
                                + " JOIN products p ON p.id=m.product_id WHERE m.performed_at<?");
                args.add(period.end());
                select.append(scope.where("m.warehouse_id", args)).append(product(productId, "m.product_id", args))
                                .append(" GROUP BY m.warehouse_id,w.code,w.name,m.product_id,p.code,p.name,p.unit,p.status");
                return page(new Query(select.toString(), args, "warehouse_id,product_id"), (r, n) -> new InventoryRow(
                                r.getObject("warehouse_id", UUID.class), r.getString("warehouse_code"),
                                r.getString("warehouse_name"),
                                r.getObject("product_id", UUID.class), r.getString("product_code"),
                                r.getString("product_name"),
                                r.getString("unit"), r.getShort("product_status"), exact(r, "opening_quantity"),
                                exact(r, "receipt_quantity"),
                                exact(r, "receipt_cancellation_quantity"), exact(r, "sale_quantity"),
                                exact(r, "sale_cancellation_quantity"),
                                exact(r, "adjustment_increase_quantity"), exact(r, "adjustment_decrease_quantity"),
                                exact(r, "closing_quantity")),
                                page, size);
        }

        private Query salesQuery(Scope scope, ReportPeriod period) {
                var args = new ArrayList<Object>();
                args.add(period.start());
                args.add(period.end());
                String confirmed = "SELECT s.warehouse_id,1 confirmed_order_count,0 cancelled_confirmed_order_count,"
                                + "COALESCE((SELECT SUM(CAST(l.quantity AS NUMERIC)) FROM sales_order_lines l WHERE l.sales_order_id=s.id),0) confirmed_quantity,"
                                + "0 cancelled_quantity,CAST(s.subtotal AS NUMERIC) gross_sales_amount,"
                                + "CAST(s.discount_amount AS NUMERIC) discount_amount,CAST(s.total_amount AS NUMERIC) confirmed_sales_amount,"
                                + "0 cancelled_sales_amount FROM sales_orders s WHERE s.confirmed_at>=? AND s.confirmed_at<?"
                                + scope.where("s.warehouse_id", args);
                args.add(period.start());
                args.add(period.end());
                String cancelled = "SELECT s.warehouse_id,0,1,0,"
                                + "COALESCE((SELECT SUM(CAST(l.quantity AS NUMERIC)) FROM sales_order_lines l WHERE l.sales_order_id=s.id),0),"
                                + "0,0,0,CAST(s.total_amount AS NUMERIC) FROM sales_orders s"
                                + " WHERE s.confirmed_at IS NOT NULL AND s.cancelled_at>=? AND s.cancelled_at<?"
                                + scope.where("s.warehouse_id", args);
                return new Query("SELECT e.warehouse_id,w.code warehouse_code,w.name warehouse_name,"
                                + sums(SALES_COLUMNS)
                                + ",SUM(confirmed_sales_amount)-SUM(cancelled_sales_amount) net_sales_amount"
                                + " FROM (" + confirmed + " UNION ALL " + cancelled
                                + ") e JOIN warehouses w ON w.id=e.warehouse_id"
                                + " GROUP BY e.warehouse_id,w.code,w.name", args, "warehouse_id");
        }

        private static final String[] SALES_COLUMNS = { "confirmed_order_count", "cancelled_confirmed_order_count",
                        "confirmed_quantity", "cancelled_quantity", "gross_sales_amount", "discount_amount",
                        "confirmed_sales_amount", "cancelled_sales_amount" };
        private static final String[] PAYMENT_COLUMNS = { "created_payment_count", "cancelled_payment_count",
                        "recorded_cash_amount", "recorded_bank_transfer_amount", "reversed_cash_amount",
                        "reversed_bank_transfer_amount" };

        private String sums(String[] columns) {
                return Arrays.stream(columns).map(c -> "COALESCE(SUM(CAST(" + c + " AS NUMERIC)),0) " + c)
                                .collect(java.util.stream.Collectors.joining(","));
        }

        private SalesSummary salesMetrics(ResultSet r) throws SQLException {
                return new SalesSummary(exact(r, "confirmed_order_count"), exact(r, "cancelled_confirmed_order_count"),
                                exact(r, "confirmed_quantity"), exact(r, "cancelled_quantity"),
                                exact(r, "gross_sales_amount"),
                                exact(r, "discount_amount"), exact(r, "confirmed_sales_amount"),
                                exact(r, "cancelled_sales_amount"),
                                exact(r, "net_sales_amount"));
        }

        public SalesSummary salesSummary(Scope scope, ReportPeriod period) {
                var q = salesQuery(scope, period);
                return jdbc.queryForObject("SELECT " + sums(SALES_COLUMNS)
                                + ",COALESCE(SUM(net_sales_amount),0) net_sales_amount FROM (" + q.sql
                                + ") report_rows",
                                (r, n) -> salesMetrics(r), q.args.toArray());
        }

        public PageResponse<SalesRow> sales(Scope scope, ReportPeriod period, int page, int size) {
                return page(salesQuery(scope, period), (r, n) -> new SalesRow(r.getObject("warehouse_id", UUID.class),
                                r.getString("warehouse_code"), r.getString("warehouse_name"), salesMetrics(r)), page,
                                size);
        }

        private Query paymentsQuery(Scope scope, ReportPeriod period) {
                var args = new ArrayList<Object>();
                args.add(period.start());
                args.add(period.end());
                String created = "SELECT p.warehouse_id,1 created_payment_count,0 cancelled_payment_count,"
                                + "CASE WHEN p.method='CASH' THEN CAST(p.amount AS NUMERIC) ELSE 0 END recorded_cash_amount,"
                                + "CASE WHEN p.method='BANK_TRANSFER' THEN CAST(p.amount AS NUMERIC) ELSE 0 END recorded_bank_transfer_amount,"
                                + "0 reversed_cash_amount,0 reversed_bank_transfer_amount FROM payments p"
                                + " WHERE p.created_at>=? AND p.created_at<?" + scope.where("p.warehouse_id", args);
                args.add(period.start());
                args.add(period.end());
                String cancelled = "SELECT p.warehouse_id,0,1,0,0,"
                                + "CASE WHEN p.method='CASH' THEN CAST(p.amount AS NUMERIC) ELSE 0 END,"
                                + "CASE WHEN p.method='BANK_TRANSFER' THEN CAST(p.amount AS NUMERIC) ELSE 0 END"
                                + " FROM payments p WHERE p.cancelled_at>=? AND p.cancelled_at<?"
                                + scope.where("p.warehouse_id", args);
                return new Query("SELECT e.warehouse_id,w.code warehouse_code,w.name warehouse_name,"
                                + sums(PAYMENT_COLUMNS)
                                + ",SUM(recorded_cash_amount)-SUM(reversed_cash_amount) net_cash_amount"
                                + ",SUM(recorded_bank_transfer_amount)-SUM(reversed_bank_transfer_amount) net_bank_transfer_amount"
                                + ",SUM(recorded_cash_amount)+SUM(recorded_bank_transfer_amount)"
                                + "-SUM(reversed_cash_amount)-SUM(reversed_bank_transfer_amount) net_recorded_amount"
                                + " FROM (" + created + " UNION ALL " + cancelled
                                + ") e JOIN warehouses w ON w.id=e.warehouse_id"
                                + " GROUP BY e.warehouse_id,w.code,w.name", args, "warehouse_id");
        }

        private PaymentSummary paymentMetrics(ResultSet r) throws SQLException {
                return new PaymentSummary(exact(r, "created_payment_count"), exact(r, "cancelled_payment_count"),
                                exact(r, "recorded_cash_amount"), exact(r, "recorded_bank_transfer_amount"),
                                exact(r, "reversed_cash_amount"), exact(r, "reversed_bank_transfer_amount"),
                                exact(r, "net_cash_amount"), exact(r, "net_bank_transfer_amount"),
                                exact(r, "net_recorded_amount"));
        }

        public PaymentSummary paymentsSummary(Scope scope, ReportPeriod period) {
                var q = paymentsQuery(scope, period);
                return jdbc.queryForObject("SELECT " + sums(PAYMENT_COLUMNS)
                                + ",COALESCE(SUM(net_cash_amount),0) net_cash_amount"
                                + ",COALESCE(SUM(net_bank_transfer_amount),0) net_bank_transfer_amount"
                                + ",COALESCE(SUM(net_recorded_amount),0) net_recorded_amount FROM (" + q.sql
                                + ") report_rows",
                                (r, n) -> paymentMetrics(r), q.args.toArray());
        }

        public PageResponse<PaymentRow> payments(Scope scope, ReportPeriod period, int page, int size) {
                return page(paymentsQuery(scope, period),
                                (r, n) -> new PaymentRow(r.getObject("warehouse_id", UUID.class),
                                                r.getString("warehouse_code"), r.getString("warehouse_name"),
                                                paymentMetrics(r)),
                                page, size);
        }

        public ReceivablesSummary receivables(Scope scope, Instant asOf) {
                var args = new ArrayList<Object>();
                String sql = "SELECT COALESCE(SUM(CASE WHEN t.customer_id IS NOT NULL THEN t.remaining_amount ELSE 0 END),0) customer_amount,"
                                + "COUNT(DISTINCT t.customer_id) customers,"
                                + "COALESCE(SUM(CASE WHEN t.customer_id IS NOT NULL THEN CAST(1 AS NUMERIC) ELSE 0 END),0) customer_orders,"
                                + "COALESCE(SUM(CASE WHEN t.customer_id IS NULL THEN t.remaining_amount ELSE 0 END),0) walk_in_amount,"
                                + "COALESCE(SUM(CASE WHEN t.customer_id IS NULL THEN CAST(1 AS NUMERIC) ELSE 0 END),0) walk_in_orders,"
                                + "COALESCE(SUM(t.remaining_amount),0) total_amount FROM sales_payment_totals t"
                                + " WHERE t.status='CONFIRMED' AND t.remaining_amount>0"
                                + scope.where("t.warehouse_id", args);
                return jdbc.queryForObject(sql,
                                (r, n) -> new ReceivablesSummary(exact(r, "customer_amount"), exact(r, "customers"),
                                                exact(r, "customer_orders"), exact(r, "walk_in_amount"),
                                                exact(r, "walk_in_orders"),
                                                exact(r, "total_amount"), asOf),
                                args.toArray());
        }

        private Query lowStockQuery(Scope scope, UUID productId) {
                var args = new ArrayList<Object>();
                return new Query("SELECT w.id warehouse_id,w.code warehouse_code,w.name warehouse_name,"
                                + "p.id product_id,p.code product_code,p.name product_name,p.unit,"
                                + "COALESCE(b.quantity,0) quantity,p.low_stock_threshold,"
                                + "CAST(p.low_stock_threshold AS NUMERIC)-COALESCE(b.quantity,0) shortage_quantity"
                                + " FROM warehouses w CROSS JOIN products p LEFT JOIN inventory_balances b"
                                + " ON b.warehouse_id=w.id AND b.product_id=p.id"
                                + " WHERE w.status=1 AND p.status=1 AND p.low_stock_threshold>0"
                                + " AND COALESCE(b.quantity,0)<p.low_stock_threshold"
                                + scope.where("w.id", args) + product(productId, "p.id", args), args,
                                "warehouse_id,product_id");
        }

        public long lowStockCount(Scope scope) {
                var q = lowStockQuery(scope, null);
                return numbers.exact(jdbc.queryForObject("SELECT count(*) FROM (" + q.sql + ") report_rows",
                                java.math.BigDecimal.class, q.args.toArray()));
        }

        public PageResponse<LowStockRow> lowStock(Scope scope, UUID productId, int page, int size) {
                return page(lowStockQuery(scope, productId),
                                (r, n) -> new LowStockRow(r.getObject("warehouse_id", UUID.class),
                                                r.getString("warehouse_code"), r.getString("warehouse_name"),
                                                r.getObject("product_id", UUID.class),
                                                r.getString("product_code"), r.getString("product_name"),
                                                r.getString("unit"),
                                                exact(r, "quantity"), exact(r, "low_stock_threshold"),
                                                exact(r, "shortage_quantity")),
                                page, size);
        }
}
