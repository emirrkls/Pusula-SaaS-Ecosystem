package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceUsedPart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@ContextConfiguration(classes = ServiceUsedPartRepositoryMissingInventoryTest.JpaTestConfiguration.class)
class ServiceUsedPartRepositoryMissingInventoryTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.pusula.backend.entity")
    @EnableJpaRepositories(basePackages = "com.pusula.backend.repository")
    static class JpaTestConfiguration {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServiceUsedPartRepository repository;

    @Test
    void returnsHistoricalUsedPartWhenInventoryRowNoLongerExists() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.update("""
                INSERT INTO service_tickets
                    (id, company_id, customer_id, status, is_deleted, created_at, updated_at)
                VALUES (100, 10, 200, 'COMPLETED', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO service_used_parts
                    (id, company_id, ticket_id, inventory_id, quantity_used,
                     selling_price_snapshot, is_deleted, created_at, updated_at)
                VALUES (300, 10, 100, 999, 2, 125.50, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        List<ServiceUsedPart> parts = assertDoesNotThrow(() -> repository.findByServiceTicketId(100L));

        assertEquals(1, parts.size());
        assertEquals(300L, parts.get(0).getId());
        assertEquals(999L, parts.get(0).getInventoryId());
        assertNull(parts.get(0).getInventory());
    }
}
