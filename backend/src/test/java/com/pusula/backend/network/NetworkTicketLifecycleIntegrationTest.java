package com.pusula.backend.network;

import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import com.pusula.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real network, ticket, receivable, timeline and notification persistence; no mocked create/close. */
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=NetworkTicketLifecycleIntegrationTest.Application.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class NetworkTicketLifecycleIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan(basePackages={"com.pusula.backend.entity","com.pusula.backend.network"})
    @EnableJpaRepositories(basePackages={"com.pusula.backend.repository","com.pusula.backend.network"})
    @Import({ServiceNetworkService.class,ServiceTicketService.class,NetworkTicketListener.class,
            AdminNotificationService.class,CurrentAccountLedgerService.class})
    static class Application { @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(4);} }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv().getOrDefault("NETWORK_TEST_JDBC_URL","jdbc:h2:mem:network_lifecycle;DB_CLOSE_DELAY=-1"));
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("NETWORK_TEST_DB_USER","sa"));
        registry.add("spring.datasource.password",()->"");
        registry.add("spring.datasource.driver-class-name",()->System.getenv("NETWORK_TEST_JDBC_URL")==null?"org.h2.Driver":"org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",()->System.getenv("NETWORK_TEST_JDBC_URL")==null?"org.hibernate.dialect.H2Dialect":"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired ServiceNetworkService network;
    @Autowired ServiceTicketService ticketService;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired ServiceTicketRepository tickets;
    @Autowired CustomerRepository customers;
    @Autowired CurrentAccountRepository accounts;
    @Autowired CurrentAccountTransactionRepository ledger;
    @Autowired NotificationRepository notifications;
    @Autowired NetworkPolicyRepository policies;
    @MockBean FeatureService features;
    @MockBean AuditLogService audit;
    @MockBean WhatsAppNotificationService whatsapp; // No external messages in tests.
    @MockBean FileUploadService files;
    @MockBean UploadUrlSigner signer;
    @MockBean FinanceService finance; // Daily aggregate reconciliation is verified as a boundary call.
    Company parent,child;
    User parentAdmin,childAdmin,technician;
    NetworkDtos.Order sent;
    LocalDate day=LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(2);

    @BeforeEach void setup() {
        parent=company("Parent");child=company("Child");
        parentAdmin=user(parent,"COMPANY_ADMIN");childAdmin=user(child,"COMPANY_ADMIN");technician=user(child,"TECHNICIAN");
        NetworkPolicy policy=new NetworkPolicy();policy.setCompanyId(parent.getId());policy.setEnabled(true);policy.setMaxMembers(10);policy.setMaxMonthlyOrders(100);policies.saveAndFlush(policy);
        login(parentAdmin);var member=network.invite(new NetworkDtos.Invite(child.getOrgCode(),"Ege"));
        login(childAdmin);network.decideInvite(member.id(),true);
        login(parentAdmin);sent=network.dispatch(new NetworkDtos.Dispatch(member.id(),UUID.randomUUID().toString(),"Service job","Customer","05550000000","Address","Private instruction",day.atTime(10,0),day.atTime(12,0)));
        login(childAdmin);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void acceptedJobUsesRealTicketLifecycleAndPostsReceivableOnlyInChildCompany() {
        var accepted=network.accept(sent.id(),new NetworkDtos.Accept(null,technician.getId()));
        Long ticketId=accepted.acceptedTicketId();
        assertEquals("ASSIGNED",accepted.ticketStatus());
        assertEquals(ticketId,network.accept(sent.id(),new NetworkDtos.Accept(null,null)).acceptedTicketId());
        assertEquals(1,customers.countByCompanyId(child.getId()));

        login(technician);
        ServiceTicketDTO progress=new ServiceTicketDTO();progress.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        progress.setNotes("Private technician note");ticketService.updateTicket(ticketId,progress);
        login(childAdmin);
        ServiceTicketDTO completed=ticketService.completeService(ticketId,new BigDecimal("500"),new BigDecimal("800"),PaymentMethod.CASH,day.plusDays(1));
        assertEquals(ServiceTicket.TicketStatus.COMPLETED,completed.getStatus());
        ServiceTicket stored=tickets.findById(ticketId).orElseThrow();
        assertEquals(0,new BigDecimal("800").compareTo(stored.getInvoiceTotal()));
        assertEquals(0,new BigDecimal("500").compareTo(stored.getCollectedAmount()));
        assertEquals(0,new BigDecimal("300").compareTo(stored.getOutstandingAmount()));
        assertEquals(day.plusDays(1),stored.getCollectionDate());
        assertTrue(stored.getTechnicianPrivateNote().contains("Private instruction"));
        var account=accounts.findByCustomerIdAndCompanyId(stored.getCustomerId(),child.getId()).orElseThrow();
        assertEquals(0,new BigDecimal("300").compareTo(account.getBalance()));
        var transactions=ledger.findByCurrentAccountIdAndCompanyIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId(),child.getId());
        assertEquals(1,transactions.size());assertEquals(day.plusDays(1),transactions.get(0).getEffectiveDate());
        assertEquals(ticketId,transactions.get(0).getSourceId());
        verify(finance).reconcileClosedDay(child.getId(),day.plusDays(1));
        assertTrue(tickets.findAll().stream().noneMatch(t->parent.getId().equals(t.getCompanyId())));
        assertTrue(accounts.findAll().stream().noneMatch(a->parent.getId().equals(a.getCompanyId())));

        login(parentAdmin);var summary=network.getOrder(sent.id());
        assertEquals("COMPLETED",summary.ticketStatus());assertEquals(day.plusDays(1),summary.completedAt().toLocalDate());
        var history=network.history(sent.id(),0).items();
        assertEquals(2,history.stream().filter(e->e.action().equals("PROGRESS")).count());
        assertFalse(history.toString().contains("Private technician note"));
        assertTrue(notifications.findTop100ByCompanyIdAndUserIdOrderByCreatedAtDesc(parent.getId(),parentAdmin.getId()).stream()
                .anyMatch(n->"NETWORK_ORDER".equals(n.getReferenceType())&&sent.id().equals(n.getReferenceId())&&n.getMessage().contains("Tamamlandı")));
        assertThrows(RuntimeException.class,()->ticketService.completeService(ticketId,BigDecimal.ZERO,PaymentMethod.CASH,null));
        login(childAdmin);assertThrows(IllegalStateException.class,()->ticketService.completeService(ticketId,BigDecimal.ZERO,PaymentMethod.CASH,null));
        assertEquals(1,ledger.findByCurrentAccountIdAndCompanyIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId(),child.getId()).size());
    }

    @Test void realCompletionFailureRollsBackTicketReceivableAndNetworkProgress() {
        var accepted=network.accept(sent.id(),new NetworkDtos.Accept(null,null));
        doThrow(new IllegalStateException("Reconciliation failed")).when(finance).reconcileClosedDay(child.getId(),day);
        assertThrows(IllegalStateException.class,()->ticketService.completeService(accepted.acceptedTicketId(),BigDecimal.ZERO,new BigDecimal("800"),PaymentMethod.CURRENT_ACCOUNT,day));
        assertEquals(ServiceTicket.TicketStatus.PENDING,tickets.findById(accepted.acceptedTicketId()).orElseThrow().getStatus());
        assertTrue(accounts.findAll().stream().noneMatch(a->child.getId().equals(a.getCompanyId())));
        assertTrue(ledger.findAll().stream().noneMatch(t->child.getId().equals(t.getCompanyId())));
        assertEquals(0,network.history(sent.id(),0).items().stream().filter(e->e.action().equals("PROGRESS")).count());
    }

    private Company company(String name){Company c=new Company();c.setName(name);c.setOrgCode(UUID.randomUUID().toString().substring(0,16));c.setSubscriptionStatus("ACTIVE");return companies.saveAndFlush(c);}
    private User user(Company c,String role){return users.saveAndFlush(User.builder().companyId(c.getId()).username(UUID.randomUUID().toString()).passwordHash("test-only").role(role).fullName(role).build());}
    private void login(User user){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
}
