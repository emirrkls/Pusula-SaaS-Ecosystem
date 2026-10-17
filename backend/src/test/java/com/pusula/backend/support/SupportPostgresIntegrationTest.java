package com.pusula.backend.support;

import com.pusula.backend.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL, disposable loopback database only; all base security/retention tests run too. */
@EnabledIfEnvironmentVariable(named="SUPPORT_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/support_validation_[a-z0-9_]+")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupportPostgresIntegrationTest extends SupportServiceIntegrationTest {
    @Autowired PlatformTransactionManager transactionManager;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url",()->System.getenv("SUPPORT_TEST_JDBC_URL"));
        props.add("spring.datasource.username",()->"support_test");
        props.add("spring.datasource.password",()->"");
        props.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        props.add("spring.datasource.hikari.maximum-pool-size",()->12);
    }
    @BeforeAll void installActualSupportMigrationInsteadOfHibernateSupportTables() throws Exception {
        String url=System.getenv("SUPPORT_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/support_validation_[a-z0-9_]+"));
        try(var connection=DriverManager.getConnection(url,"support_test","");var sql=connection.createStatement()) {
            // Only the five newly introduced tables in this task's empty, isolated test database.
            sql.execute("DROP TABLE support_mail_outbox,support_attachments,support_events,support_messages,support_tickets");
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V49__support_center.sql"));
            try(var rows=sql.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname='ix_support_mail_due'")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
        }
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void eightConcurrentLostResponseReplaysCommitOneTicketOneMessageAndOneOutboxEntry() throws Exception {
        var tx=new TransactionTemplate(transactionManager);
        var user=tx.execute(status->{
            var company=new Company();company.setName("Postgres isolated QA");company.setSubscriptionStatus("ACTIVE");company=companies.saveAndFlush(company);
            var actor=new User();actor.setCompanyId(company.getId());actor.setUsername(UUID.randomUUID()+"@example.invalid");actor.setFullName("Synthetic QA");actor.setPasswordHash("not-a-credential");actor.setRole("TECHNICIAN");return users.saveAndFlush(actor);
        });
        String key=UUID.randomUUID().toString();var input=new SupportDtos.Create(key,"Concurrent replay","BUG","Synthetic test","IOS","1",List.of());
        var pool=Executors.newFixedThreadPool(8);var ready=new CyclicBarrier(8);
        try {
            var futures=new ArrayList<Future<Long>>();
            for(int i=0;i<8;i++) futures.add(pool.submit(()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
                try {ready.await(10,TimeUnit.SECONDS);return service.create(input).ticket().id();}finally{SecurityContextHolder.clearContext();}
            }));
            var ids=new HashSet<Long>();for(var result:futures)ids.add(result.get(25,TimeUnit.SECONDS));assertEquals(1,ids.size());
            assertEquals(1,tickets.count());assertEquals(1,messages.count());assertEquals(1,mails.count());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));privacy.eraseUser(user.getId());}
    }
}
