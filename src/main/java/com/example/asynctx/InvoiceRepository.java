package com.example.asynctx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceRepository {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS invoice (
                    invoice_id VARCHAR(40) PRIMARY KEY
                )
                """);
    }

    public void save(String invoiceId) {
        jdbcTemplate.update("INSERT INTO invoice (invoice_id) VALUES (?)", invoiceId);
    }

    public boolean exists(String invoiceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice WHERE invoice_id = ?",
                Integer.class,
                invoiceId
        );
        return count != null && count == 1;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM invoice");
    }
}
