package com.pusula.backend.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual migration, including PostgreSQL partial indexes and check constraints. */
@EnabledIfEnvironmentVariable(named="NETWORK_TEST_JDBC_URL",matches="jdbc:postgresql:.*")
class NetworkMigrationPostgresTest {
    @Test void migrationSupportsLifecycleAndRejectsInvalidLedgerState() throws Exception {
        String schema="network_migration_"+UUID.randomUUID().toString().replace("-","");
        try(Connection connection=DriverManager.getConnection(System.getenv("NETWORK_TEST_JDBC_URL"),System.getenv().getOrDefault("NETWORK_TEST_DB_USER","network_test"),"");
                Statement sql=connection.createStatement()) {
            sql.execute("CREATE SCHEMA "+schema);
            try {
                sql.execute("SET search_path TO "+schema);
                sql.execute("CREATE TABLE companies(id BIGINT PRIMARY KEY)");
                sql.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
                sql.execute("CREATE TABLE service_tickets(id BIGINT PRIMARY KEY)");
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V35__service_network.sql"));
                sql.execute("INSERT INTO companies VALUES (1),(2),(3)");
                sql.execute("INSERT INTO users VALUES (1)");
                sql.execute("INSERT INTO service_tickets VALUES (100)");
                sql.execute("INSERT INTO service_network_policies(company_id,enabled,max_members,max_monthly_orders) VALUES(1,true,400,20000)");
                assertThrows(SQLException.class,()->sql.execute("INSERT INTO service_network_policies(company_id,max_members) VALUES(2,-1)"));
                sql.execute("INSERT INTO service_network_memberships(id,parent_company_id,child_company_id,parent_name,child_name,status,created_at) VALUES(1,1,2,'Parent','Child','INVITED',now())");
                assertThrows(SQLException.class,()->sql.execute("INSERT INTO service_network_memberships(parent_company_id,child_company_id,parent_name,child_name,status,created_at) VALUES(3,2,'Other','Child','ACTIVE',now())"));
                sql.execute("UPDATE service_network_memberships SET status='ACTIVE' WHERE id=1");
                String insert="INSERT INTO service_network_orders(membership_id,parent_company_id,child_company_id,parent_name,child_name,request_key,title,customer_name,scheduled_date,status,created_at) VALUES(1,1,2,'Parent','Child','retry-key','Job','Customer',now(),'SENT',now())";
                sql.execute(insert);
                assertThrows(SQLException.class,()->sql.execute(insert));
                assertThrows(SQLException.class,()->sql.execute("UPDATE service_network_orders SET status='ACCEPTED'"));
                sql.execute("UPDATE service_network_orders SET status='ACCEPTED',accepted_ticket_id=100");
                assertThrows(SQLException.class,()->sql.execute("UPDATE service_network_orders SET scheduled_end_date=scheduled_date - INTERVAL '1 hour'"));
                sql.execute("INSERT INTO service_network_order_events(order_id,actor_company_id,actor_user_id,action,note,created_at) SELECT id,2,1,'ACCEPTED','Accepted',now() FROM service_network_orders");
                sql.execute("UPDATE service_network_memberships SET status='CLOSED' WHERE id=1");
                sql.execute("INSERT INTO service_network_memberships(parent_company_id,child_company_id,parent_name,child_name,status,created_at) VALUES(3,2,'Other','Child','ACTIVE',now())");
                try(ResultSet result=sql.executeQuery("SELECT count(*) FROM service_network_order_events")){assertTrue(result.next());assertEquals(1,result.getInt(1));}
            } finally {
                sql.execute("SET search_path TO public");
                // Only this test-created, UUID-named schema; never drop application tables.
                sql.execute("DROP SCHEMA "+schema+" CASCADE");
            }
        }
    }
}
