package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import com.pusula.backend.dto.ReliableExpenseCreateRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ReliableMutationServiceIntegrationTest.Application.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReliableMutationServiceIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan("com.pusula.backend.entity")
    @EnableJpaRepositories("com.pusula.backend.repository")
    @Import({ReliableMutationService.class, FinanceService.class, AuditLogService.class})
    static class Application { @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); } }

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        boolean postgres = System.getenv("RELIABLE_TEST_JDBC_URL") != null;
        registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("RELIABLE_TEST_JDBC_URL",
                "jdbc:h2:mem:reliable;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("RELIABLE_TEST_DB_USER", "sa"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("RELIABLE_TEST_DB_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> postgres ? "org.postgresql.Driver" : "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> postgres
                ? "org.hibernate.dialect.PostgreSQLDialect" : "org.hibernate.dialect.H2Dialect");
    }

    @Autowired ReliableMutationService reliable;
    @Autowired MutationReceiptRepository receipts;
    @Autowired CustomerRepository customers;
    @Autowired ExpenseRepository expenses;
    @Autowired DailyClosingRepository closings;
    @Autowired AuditLogRepository audits;
    @Autowired FinanceService finance;
    @MockBean FeatureService features;
    @Autowired JdbcTemplate jdbc;
    final AtomicInteger writes = new AtomicInteger();
    User actor;

    @BeforeEach void setup() {
        receipts.deleteAll(); customers.deleteAll(); expenses.deleteAll(); closings.deleteAll(); audits.deleteAll(); writes.set(0);
        actor = user(10L, 1L); login(actor);
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    private User user(long id, long company) {
        User user = new User(); user.setId(id); user.setCompanyId(company); user.setRole("COMPANY_ADMIN");
        user.setUsername("synthetic-actor-" + id); user.setFullName("Synthetic test actor");
        return user;
    }
    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    private Customer create(String name) {
        writes.incrementAndGet();
        Customer customer = new Customer(); customer.setCompanyId(actor.getCompanyId()); customer.setName(name);
        return customers.saveAndFlush(customer);
    }
    private Customer perform(String key, String operation, Object input) {
        Long company = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        return reliable.execute(key, operation, input, () -> {
            writes.incrementAndGet(); Customer value = new Customer(); value.setCompanyId(company); value.setName("Test");
            return customers.saveAndFlush(value);
        }, Customer::getId, id -> customers.findByIdAndCompanyId(id, company).orElse(null));
    }

    @Test void lostResponseAndManualRetryReloadOneResource() {
        String key = UUID.randomUUID().toString();
        Long first = perform(key, "customer.create", Map.of("name", "Test")).getId();
        assertEquals(first, perform(key.toUpperCase(Locale.ROOT), "customer.create", Map.of("name", "Test")).getId());
        assertEquals(1, writes.get()); assertEquals(1, customers.count()); assertEquals(1, receipts.count());
    }

    @Test void concurrentCopiesCommitExactlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> {
                login(actor); ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { return perform(key, "ticket.create", Map.of("customerId", 50)).getId(); }
                finally { logout(); }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            Set<Long> ids = new HashSet<>();
            for (Future<Long> result : results) ids.add(result.get(20, TimeUnit.SECONDS));
            assertEquals(1, ids.size()); assertEquals(1, writes.get()); assertEquals(1, customers.count());
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void failedTransactionRollsBackBothReceiptAndBusinessWrite() {
        String key = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> reliable.execute(key, "customer.create", Map.of("a", 1),
                () -> { create("Rollback"); throw new IllegalStateException("Simulated failure after insert"); },
                Customer::getId, id -> customers.findById(id).orElse(null)));
        assertEquals(0, customers.count()); assertEquals(0, receipts.count());
        perform(key, "customer.create", Map.of("a", 1));
        assertEquals(1, customers.count()); assertEquals(1, receipts.count());
    }

    @Test void sameKeyCannotChangePayloadOrOperation() {
        String key = UUID.randomUUID().toString(); perform(key, "customer.create", Map.of("a", 1));
        assertEquals("IDEMPOTENCY_MISMATCH", assertThrows(MutationConflictException.class,
                () -> perform(key, "customer.create", Map.of("a", 2))).getCode());
        assertThrows(MutationConflictException.class, () -> perform(key, "ticket.create", Map.of("a", 1)));
        assertEquals(1, writes.get());
    }

    @Test void keysAreScopedToBothTenantAndUser() {
        String key = UUID.randomUUID().toString();
        Long first = perform(key, "customer.create", Map.of("a", 1)).getId();
        login(user(11, 1)); assertNotEquals(first, perform(key, "customer.create", Map.of("a", 1)).getId());
        login(user(10, 2)); assertNotEquals(first, perform(key, "customer.create", Map.of("a", 1)).getId());
        assertEquals(3, receipts.count()); assertEquals(3, writes.get());
    }

    @Test void freshIntentWithIdenticalContentIsNotIncorrectlyDeduplicated() {
        Long first = perform(UUID.randomUUID().toString(), "ticket.create", Map.of("a", 1)).getId();
        assertNotEquals(first, perform(UUID.randomUUID().toString(), "ticket.create", Map.of("a", 1)).getId());
        assertEquals(2, writes.get());
    }

    @Test void expiredKeyDoesNotCreateAnotherRecord() {
        String key = UUID.randomUUID().toString(); perform(key, "customer.create", Map.of("a", 1));
        jdbc.update("UPDATE mutation_receipts SET created_at=?", Timestamp.from(Instant.now().minus(Duration.ofDays(2))));
        assertEquals("IDEMPOTENCY_EXPIRED", assertThrows(MutationConflictException.class,
                () -> perform(key, "customer.create", Map.of("a", 1))).getCode());
        assertEquals(1, writes.get());
    }

    @Test void inaccessibleOriginalResultNeverRunsMutationAgain() {
        String key = UUID.randomUUID().toString(); perform(key, "customer.create", Map.of("a", 1));
        assertEquals("IDEMPOTENCY_RESULT_UNAVAILABLE", assertThrows(MutationConflictException.class,
                () -> reliable.execute(key, "customer.create", Map.of("a", 1), () -> create("Duplicate"),
                        Customer::getId, id -> null)).getCode());
        assertEquals(1, writes.get());
    }

    @Test void anonymousAndMalformedKeysCannotCreateRows() {
        assertThrows(IllegalArgumentException.class, () -> perform("not-a-uuid", "customer.create", Map.of()));
        logout(); assertThrows(AccessDeniedException.class,
                () -> performWithoutPrincipal(UUID.randomUUID().toString()));
        assertEquals(0, receipts.count());
    }
    private Customer performWithoutPrincipal(String key) {
        return reliable.execute(key, "customer.create", Map.of(), () -> create("Invalid"), Customer::getId, id -> null);
    }

    @Test void receiptFingerprintIsCanonicalWithoutPersistingBody() {
        Map<String, Object> first = new LinkedHashMap<>(); first.put("z", Map.of("b", 2, "a", 1)); first.put("a", "SECRET");
        Map<String, Object> second = new LinkedHashMap<>(); second.put("a", "SECRET"); second.put("z", Map.of("a", 1, "b", 2));
        assertEquals(reliable.fingerprint(first), reliable.fingerprint(second));
        assertEquals(64, reliable.fingerprint(first).length());
        assertFalse(reliable.fingerprint(first).contains("SECRET"));
    }

    private ReliableExpenseCreateRequest expenseInput(String amount) {
        return new ReliableExpenseCreateRequest(new BigDecimal(amount), "Synthetic expense",
                LocalDate.of(2026,10,14), ExpenseCategory.OTHER, PaymentMethod.CASH,
                ExpenseTreatment.OPERATING_EXPENSE);
    }
    private Expense expense(String key, ReliableExpenseCreateRequest input) {
        Long company = ((User)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        return reliable.execute(key,"expense.create",input,
                () -> finance.addExpense(input.toExpense(company)), Expense::getId,
                id -> expenses.findByIdAndCompanyId(id,company).orElse(null));
    }
    private DailyClosing closedDay() {
        return closings.saveAndFlush(DailyClosing.builder().companyId(1L).date(LocalDate.of(2026,10,14))
                .status(DailyClosing.ClosingStatus.CLOSED).totalIncome(BigDecimal.ZERO)
                .totalExpense(BigDecimal.ZERO).netCash(BigDecimal.ZERO).build());
    }
    private long auditCount(String action, String entity) {
        return audits.findAll().stream().filter(a -> action.equals(a.getActionType()) && entity.equals(a.getEntityType())).count();
    }

    @Test void concurrentExpenseCopiesWriteAndReconcileClosedDayExactlyOnce() throws Exception {
        DailyClosing closing = closedDay(); String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for(int i=0;i<8;i++) results.add(pool.submit(() -> {
                login(actor); ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS));
                try { return expense(key,expenseInput("125.50")).getId(); } finally { logout(); }
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            Set<Long> ids = new HashSet<>();
            for(Future<Long> result:results) ids.add(result.get(20,TimeUnit.SECONDS));
            assertEquals(1,ids.size()); assertEquals(1,expenses.count()); assertEquals(1,receipts.count());
            assertEquals(1,auditCount("CREATE","EXPENSE")); assertEquals(1,auditCount("RECONCILE","DAY_CLOSING"));
            DailyClosing saved = closings.findById(closing.getId()).orElseThrow();
            assertEquals(0,new BigDecimal("125.50").compareTo(saved.getTotalExpense()));
            assertEquals(0,new BigDecimal("-125.50").compareTo(saved.getNetCash()));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void expenseRollbackRestoresReceiptAuditAndClosedDayThenRetrySucceedsOnce() {
        DailyClosing closing = closedDay(); String key=UUID.randomUUID().toString();
        ReliableExpenseCreateRequest input=expenseInput("125.50");
        assertThrows(IllegalStateException.class, () -> reliable.execute(key,"expense.create",input,() -> {
            finance.addExpense(input.toExpense(1L));
            throw new IllegalStateException("Simulated failure after expense and reconciliation");
        }, Expense::getId,id -> null));
        assertEquals(0,expenses.count()); assertEquals(0,receipts.count()); assertEquals(0,audits.count());
        assertEquals(0,BigDecimal.ZERO.compareTo(closings.findById(closing.getId()).orElseThrow().getTotalExpense()));
        Long id=expense(key,input).getId(); assertEquals(id,expense(key,input).getId());
        assertEquals(1,expenses.count()); assertEquals(1,auditCount("CREATE","EXPENSE"));
        assertEquals(1,auditCount("RECONCILE","DAY_CLOSING"));
    }

    @Test void changedExpenseAmountOrDeletedResultCannotCreateAnotherFinancialWrite() {
        String key=UUID.randomUUID().toString(); Expense saved=expense(key,expenseInput("125.50"));
        assertEquals("IDEMPOTENCY_MISMATCH",assertThrows(MutationConflictException.class,
                () -> expense(key,expenseInput("126.50"))).getCode());
        assertEquals(1,expenses.count()); assertEquals(1,auditCount("CREATE","EXPENSE"));
        expenses.deleteById(saved.getId());
        assertEquals("IDEMPOTENCY_RESULT_UNAVAILABLE",assertThrows(MutationConflictException.class,
                () -> expense(key,expenseInput("125.50"))).getCode());
        assertEquals(0,expenses.count()); assertEquals(1,auditCount("CREATE","EXPENSE"));
    }

    @Test void deliberateNewExpenseWithIdenticalContentRemainsAllowed() {
        Long first=expense(UUID.randomUUID().toString(),expenseInput("125.50")).getId();
        assertNotEquals(first,expense(UUID.randomUUID().toString(),expenseInput("125.50")).getId());
        assertEquals(2,expenses.count()); assertEquals(2,auditCount("CREATE","EXPENSE"));
        assertEquals(0,new BigDecimal("251.00").compareTo(finance.getDailySummary(1L,LocalDate.of(2026,10,14)).getTotalExpense()));
    }
}
