package com.smartparking.backend.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;

@Configuration
@RequiredArgsConstructor
public class DatabaseUpgradeConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUpgradeConfig.class);
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void upgradeDatabase() {
        try {
            log.info("🔄 DatabaseUpgradeConfig: Checking and upgrading database constraints...");
            // Drop constraint if exists (PostgreSQL syntax)
            jdbcTemplate.execute("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check");
            log.info("✅ DatabaseUpgradeConfig: Successfully dropped payments_payment_method_check constraint.");
        } catch (Exception e) {
            log.error("❌ DatabaseUpgradeConfig: Failed to drop constraint: {}", e.getMessage(), e);
        }
    }
}
