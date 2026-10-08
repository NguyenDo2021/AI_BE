package com.frontendbase.api;
import org.springframework.jdbc.core.JdbcTemplate;
final class StockTestCleanup {
    private StockTestCleanup() {}
    static void clear(JdbcTemplate jdbc) {
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if ("PostgreSQL".equals(product)) {
            // TRUNCATE is only used for the isolated test database and bypasses audit DELETE guards.
            jdbc.execute("TRUNCATE inventory_movements, inventory_balances, stock_receipt_lines, stock_receipts");
            return;
        }
        jdbc.execute("DELETE FROM inventory_movements");
        jdbc.execute("DELETE FROM inventory_balances");
        jdbc.execute("DELETE FROM stock_receipt_lines");
        jdbc.execute("DELETE FROM stock_receipts");
    }
}
