package com.pusula.backend.network;

import com.pusula.backend.entity.*;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.repository.*;
import com.pusula.backend.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.pusula.backend.network.NetworkDtos.*;

@DataJpaTest(showSql=false, properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=ServiceNetworkIntegrationTest.Application.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ServiceNetworkIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan(basePackages={"com.pusula.backend.entity","com.pusula.backend.network"})
    @EnableJpaRepositories(basePackages={"com.pusula.backend.repository","com.pusula.backend.network"})
    @Import({ServiceNetworkService.class,NetworkTicketListener.class})
    static class Application { @Bean PasswordEncoder encoder(){ return new BCryptPasswordEncoder(4); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv().getOrDefault("NETWORK_TEST_JDBC_URL","jdbc:h2:mem:network;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"));
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("NETWORK_TEST_DB_USER","sa"));
        registry.add("spring.datasource.password",()->"");
        registry.add("spring.datasource.driver-class-name",()->System.getenv("NETWORK_TEST_JDBC_URL")==null?"org.h2.Driver":"org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",()->System.getenv("NETWORK_TEST_JDBC_URL")==null?"org.hibernate.dialect.H2Dialect":"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired ServiceNetworkService service;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired ServiceTicketRepository tickets;
    @Autowired NetworkMembershipRepository members;
    @Autowired NetworkPolicyRepository policies;
    @Autowired NetworkOrderRepository orders;
    @Autowired NetworkTicketListener listener;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.context.ApplicationEventPublisher publisher;
    @Autowired PasswordEncoder passwords;
    @MockBean ServiceTicketService ticketService;
    @MockBean FeatureService features;
    @MockBean AdminNotificationService notifications;
    @MockBean AuditLogService audit;
    Company parent,child,other;
    User parentAdmin,childAdmin,outsider,technician;
    AtomicInteger created;

    @BeforeEach void setup() {
        parent=company("Parent");child=company("Child");other=company("Other");
        parentAdmin=user(parent,"COMPANY_ADMIN");childAdmin=user(child,"COMPANY_ADMIN");outsider=user(other,"COMPANY_ADMIN");technician=user(child,"TECHNICIAN");
        policy(parent,400,5000);
        created=new AtomicInteger();
        when(ticketService.createTicket(any())).thenAnswer(invocation->{
            ServiceTicketDTO dto=invocation.getArgument(0);
            User actor=(User)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            ServiceTicket ticket=new ServiceTicket();ticket.setCompanyId(actor.getCompanyId());ticket.setCustomerId(dto.getCustomerId());
            ticket.setDescription(dto.getDescription());ticket.setScheduledDate(dto.getScheduledDate());ticket.setScheduledEndDate(dto.getScheduledEndDate());
            ticket.setTechnicianPrivateNote(dto.getTechnicianPrivateNote());ticket.setAssignedTechnicianId(dto.getAssignedTechnicianId());
            ticket.setStatus(dto.getAssignedTechnicianId()==null?ServiceTicket.TicketStatus.PENDING:ServiceTicket.TicketStatus.ASSIGNED);
            tickets.saveAndFlush(ticket);created.incrementAndGet();
            publisher.publishEvent(new NetworkTicketChanged(ticket.getId(),ticket.getCompanyId(),actor.getId(),ticket.getStatus().name(),ticket.getScheduledDate(),ticket.getScheduledEndDate()));
            ServiceTicketDTO result=new ServiceTicketDTO();result.setId(ticket.getId());return result;
        });
        login(parentAdmin);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void entitlementIsOptInAndCannotBeChangedByCompanyAdmin() {
        login(outsider);assertFalse(service.context().canManage());
        assertThrows(IllegalStateException.class,()->service.invite(new Invite(child.getOrgCode(),"")));
        assertThrows(AccessDeniedException.class,()->service.configure(other.getId(),new PolicyRequest(true,400,20000)));
        outsider.setRole("SUPER_ADMIN");login(outsider);service.configure(other.getId(),new PolicyRequest(true,400,20000));
        assertTrue(service.context().canManage());assertEquals(400,service.context().maxMembers());
    }
    @Test void technicianAndAnonymousCannotUseNetwork() {
        login(technician);assertThrows(AccessDeniedException.class,()->service.context());
        assertThrows(AccessDeniedException.class,()->service.listOrders("incoming",null,null,null,null,0));
        SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,()->service.listMembers(0,null));
    }
    @Test void onlyRecipientCanAcceptInvitationAndUnrelatedTenantCannotReadIt() {
        Member invitation=service.invite(new Invite(child.getOrgCode(),"Ege"));
        assertEquals("INVITED",invitation.status());assertEquals(1,service.context().usedMembers());
        assertThrows(AccessDeniedException.class,()->service.decideInvite(invitation.id(),true));
        login(outsider);assertEquals(0,service.listMembers(0,null).totalElements());
        assertThrows(AccessDeniedException.class,()->service.decideInvite(invitation.id(),true));
        login(childAdmin);assertEquals("ACTIVE",service.decideInvite(invitation.id(),true).status());
        assertEquals("ACTIVE",service.decideInvite(invitation.id(),true).status());
    }
    @Test void limitsCountPendingInvitationsAndPreventSelfAndNestedNetworks() {
        policy(parent,1,5000);
        Member invitation=service.invite(new Invite(child.getOrgCode(),""));
        assertThrows(IllegalStateException.class,()->service.invite(new Invite(other.getOrgCode(),"")));
        assertThrows(IllegalArgumentException.class,()->service.invite(new Invite(parent.getOrgCode(),"")));
        login(childAdmin);service.decideInvite(invitation.id(),true);policy(child,400,5000);
        assertThrows(IllegalStateException.class,()->service.invite(new Invite(other.getOrgCode(),"")));
        assertThrows(IllegalStateException.class,()->service.createChild(new CreateChild(UUID.randomUUID().toString(),"Nested","","Admin","admin","Password123")));
    }
    @Test void newChildHasIndependentTrialAndHashedCredentials() {
        CreatedChild result=service.createChild(new CreateChild(UUID.randomUUID().toString(),"Fresh service","Ege","Service Admin","admin","StrongPass123"));
        Company company=companies.findById(result.member().childCompanyId()).orElseThrow();
        assertEquals(PlanType.CIRAK,company.getPlanType());assertEquals("TRIAL",company.getSubscriptionStatus());
        assertNotNull(company.getTrialEndsAt());assertNotEquals(parent.getId(),company.getId());
        User admin=users.findByUsernameAndCompanyId("admin",company.getId()).orElseThrow();
        assertEquals("COMPANY_ADMIN",admin.getRole());assertTrue(passwords.matches("StrongPass123",admin.getPasswordHash()));
        assertFalse(result.toString().contains("StrongPass123"));
    }
    @Test void weakPasswordRollsBackChildCreation() {
        long count=companies.count();
        assertThrows(IllegalArgumentException.class,()->service.createChild(new CreateChild(UUID.randomUUID().toString(),"Bad","","Admin","admin","12345")));
        assertEquals(count,companies.count());assertEquals(0,service.context().usedMembers());
    }
    @Test void idempotentDispatchUsesOneMonthlySlotAndDoesNotCreateFinancialTicket() {
        Member member=active();policy(parent,400,1);Dispatch request=request(member.id(),"stable-key");
        Order first=service.dispatch(request);Order second=service.dispatch(request);
        assertEquals(first.id(),second.id());assertEquals(1,service.context().usedMonthlyOrders());
        assertEquals(0,created.get());assertEquals(0,tickets.findAll().stream().filter(t->parent.getId().equals(t.getCompanyId())).count());
        assertThrows(IllegalStateException.class,()->service.dispatch(request(member.id(),"another-key")));
        Dispatch changed=new Dispatch(member.id(),"stable-key","Different",request.customerName(),"","","",request.scheduledDate(),null);
        assertThrows(IllegalStateException.class,()->service.dispatch(changed));
    }
    @Test void rejectsInvalidScheduleBeforeCreatingOrder() {
        Member member=active();Dispatch original=request(member.id(),UUID.randomUUID().toString());
        Dispatch invalid=new Dispatch(member.id(),original.requestKey(),"Job","Customer","","","",original.scheduledDate(),original.scheduledDate().minusMinutes(1));
        assertThrows(IllegalArgumentException.class,()->service.dispatch(invalid));assertEquals(0,service.context().usedMonthlyOrders());
    }
    @Test void acceptCreatesOneChildTicketWithPrivateInstructionAndNoSenderFinance() {
        Order sent=sent();login(childAdmin);
        Order accepted=service.accept(sent.id(),new Accept(null,technician.getId()));
        assertEquals("ACCEPTED",accepted.status());assertEquals("ASSIGNED",accepted.ticketStatus());
        ServiceTicket ticket=tickets.findById(accepted.acceptedTicketId()).orElseThrow();
        assertEquals(child.getId(),ticket.getCompanyId());assertTrue(ticket.getTechnicianPrivateNote().contains("Internal instruction"));
        assertNull(ticket.getNotes());assertNull(ticket.getInvoiceTotal());
        assertEquals(child.getId(),customers.findById(ticket.getCustomerId()).orElseThrow().getCompanyId());
        assertEquals(accepted.acceptedTicketId(),service.accept(sent.id(),new Accept(null,null)).acceptedTicketId());
        assertEquals(1,created.get());verify(features).checkQuota(child.getId(),"CUSTOMERS");
    }
    @Test void acceptanceRejectsCrossTenantCustomerAndTechnician() {
        Order sent=sent();Customer foreign=customer(other);login(childAdmin);
        assertThrows(AccessDeniedException.class,()->service.accept(sent.id(),new Accept(foreign.getId(),null)));
        assertThrows(AccessDeniedException.class,()->service.accept(sent.id(),new Accept(null,outsider.getId())));
        assertEquals(0,created.get());assertEquals("SENT",service.getOrder(sent.id()).status());
    }
    @Test void canReuseExistingChildCustomerWithoutCreatingDuplicate() {
        Order sent=sent();Customer local=customer(child);login(childAdmin);
        long before=customers.countByCompanyId(child.getId());
        Order accepted=service.accept(sent.id(),new Accept(local.getId(),null));
        assertEquals(local.getId(),tickets.findById(accepted.acceptedTicketId()).orElseThrow().getCustomerId());
        assertEquals(before,customers.countByCompanyId(child.getId()));verify(features,never()).checkQuota(child.getId(),"CUSTOMERS");
    }
    @Test void quotaFailureRollsBackAcceptanceAndCustomerCreation() {
        Order sent=sent();login(childAdmin);long before=customers.countByCompanyId(child.getId());
        doThrow(new IllegalStateException("Ticket quota")).when(features).checkQuota(child.getId(),"TICKETS");
        assertThrows(IllegalStateException.class,()->service.accept(sent.id(),new Accept(null,null)));
        assertEquals(before,customers.countByCompanyId(child.getId()));assertEquals("SENT",service.getOrder(sent.id()).status());
    }
    @Test void downstreamTicketFailureRollsBackNewCustomer() {
        Order sent=sent();login(childAdmin);long before=customers.countByCompanyId(child.getId());
        doThrow(new IllegalStateException("Local ticket failure")).when(ticketService).createTicket(any());
        assertThrows(IllegalStateException.class,()->service.accept(sent.id(),new Accept(null,null)));
        assertEquals(before,customers.countByCompanyId(child.getId()));assertEquals("SENT",service.getOrder(sent.id()).status());
    }
    @Test void outsiderCannotReadMutateOrEnumerateOrderHistory() {
        Order sent=sent();login(outsider);
        assertThrows(AccessDeniedException.class,()->service.getOrder(sent.id()));
        assertThrows(AccessDeniedException.class,()->service.history(sent.id(),0));
        assertThrows(AccessDeniedException.class,()->service.addNote(sent.id(),new Note("Intrusion")));
        assertThrows(AccessDeniedException.class,()->service.accept(sent.id(),new Accept(null,null)));
        assertEquals(0,service.listOrders("incoming",null,null,null,null,0).totalElements());
        assertEquals(0,service.listOrders("outgoing",null,null,null,null,0).totalElements());
    }
    @Test void searchDatePaginationAndLiteralWildcardsRemainScoped() {
        Order sent=sent();
        assertEquals(1,service.listOrders("outgoing","Customer",NetworkOrder.Status.SENT,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),0).totalElements());
        assertEquals(0,service.listOrders("outgoing","%",null,null,null,0).totalElements());
        assertEquals(0,service.listOrders("outgoing",null,null,LocalDate.of(2026,11,1),null,0).totalElements());
        assertEquals(0,service.listOrders("outgoing",null,null,null,null,1).items().size());
        assertEquals(1,service.listOrders("outgoing",String.valueOf(sent.id()),null,null,null,0).totalElements());
        assertThrows(IllegalArgumentException.class,()->service.listOrders("anything",null,null,null,null,0));
    }
    @Test void onlyCorrectPartyCanResolvePendingOrderAndCannotResolveAcceptedOrder() {
        Order sent=sent();assertThrows(AccessDeniedException.class,()->service.resolve(sent.id(),new Note("No"),false));
        login(childAdmin);assertThrows(AccessDeniedException.class,()->service.resolve(sent.id(),new Note("No"),true));
        assertEquals("REJECTED",service.resolve(sent.id(),new Note("No capacity"),false).status());
        assertEquals("REJECTED",service.resolve(sent.id(),new Note("Repeat"),false).status());
        assertThrows(IllegalStateException.class,()->service.accept(sent.id(),new Accept(null,null)));
        login(parentAdmin);Member member=service.listMembers(0,null).items().get(0);
        Order next=service.dispatch(request(member.id(),UUID.randomUUID().toString()));login(childAdmin);service.accept(next.id(),new Accept(null,null));
        login(parentAdmin);assertThrows(IllegalStateException.class,()->service.resolve(next.id(),new Note("Cannot"),true));
    }
    @Test void openOrdersPreventUnlinkAndClosedTicketAllowsUnlink() {
        Order sent=sent();assertThrows(IllegalStateException.class,()->service.closeMember(sent.membershipId(),new Note("Close")));
        login(childAdmin);Order accepted=service.accept(sent.id(),new Accept(null,null));
        assertThrows(IllegalStateException.class,()->service.closeMember(sent.membershipId(),new Note("Close")));
        ServiceTicket ticket=tickets.findById(accepted.acceptedTicketId()).orElseThrow();ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);tickets.save(ticket);
        assertEquals("CLOSED",service.closeMember(sent.membershipId(),new Note("Work finished")).status());
        assertEquals(accepted.id(),service.getOrder(sent.id()).id());
    }
    @Test void expiredChildIsReadOnlyButCanReadExistingHistory() {
        Order sent=sent();child.setIsReadOnly(true);companies.save(child);login(childAdmin);
        assertFalse(service.context().writable());assertNotNull(service.getOrder(sent.id()));
        assertThrows(IllegalStateException.class,()->service.accept(sent.id(),new Accept(null,null)));
        assertThrows(IllegalStateException.class,()->service.addNote(sent.id(),new Note("Cannot")));
    }
    @Test void sharedTimelineIncludesNotesAndProgressButNotPrivateTicketData() {
        Order sent=sent();login(childAdmin);Order accepted=service.accept(sent.id(),new Accept(null,null));
        service.addNote(sent.id(),new Note("Part ordered"));
        NetworkTicketChanged change=new NetworkTicketChanged(accepted.acceptedTicketId(),child.getId(),childAdmin.getId(),"IN_PROGRESS",LocalDateTime.of(2026,10,6,14,0),null);
        new TransactionTemplate(transactions).executeWithoutResult(s->{listener.changed(change);listener.changed(change);});
        login(parentAdmin);List<Event> history=service.history(sent.id(),0).items();
        assertEquals(4,history.size());assertEquals(1,history.stream().filter(e->"PROGRESS".equals(e.action())).count());
        assertFalse(history.toString().contains("Internal instruction"));
        assertTrue(history.stream().anyMatch(e->"Part ordered".equals(e.note())));
    }
    @Test void concurrentAcceptanceCreatesExactlyOneTicket() throws Exception {
        Order sent=sent();ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            Callable<Long> accept=()->{login(childAdmin);ready.countDown();assertTrue(go.await(5,TimeUnit.SECONDS));try{return service.accept(sent.id(),new Accept(null,null)).acceptedTicketId();}finally{SecurityContextHolder.clearContext();}};
            Future<Long> a=executor.submit(accept),b=executor.submit(accept);assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertEquals(1,created.get());
        } finally { executor.shutdownNow(); }
    }

    @Test void fourHundredServiceDirectoryRemainsPagedAndEnforcesCapacity() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction->{
            for(int i=0;i<400;i++) {
                Company local=company("Regional service "+i);
                NetworkMembership member=new NetworkMembership();member.setParentCompanyId(parent.getId());member.setChildCompanyId(local.getId());
                member.setParentName(parent.getName());member.setChildName(local.getName());member.setStatus(NetworkMembership.Status.ACTIVE);
                member.setCreatedAt(LocalDateTime.now());members.save(member);
            }
        });
        PageResult<Member> first=service.listMembers(0,null),last=service.listMembers(7,null);
        assertEquals(400,first.totalElements());assertEquals(50,first.items().size());assertTrue(first.hasNext());
        assertEquals(50,last.items().size());assertFalse(last.hasNext());assertEquals(8,first.totalPages());
        assertEquals(400,service.context().usedMembers());
        assertThrows(IllegalStateException.class,()->service.invite(new Invite(other.getOrgCode(),"")));
        login(outsider);assertEquals(0,service.listMembers(0,null).totalElements());
    }

    @Test void concurrentDispatchCannotOvershootMonthlyLimit() throws Exception {
        Member member=active();policy(parent,400,1);ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> dispatch=()->{login(parentAdmin);try{service.dispatch(request(member.id(),UUID.randomUUID().toString()));return true;}catch(IllegalStateException full){return false;}finally{SecurityContextHolder.clearContext();}};
            Future<Boolean> a=executor.submit(dispatch),b=executor.submit(dispatch);
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(1,service.context().usedMonthlyOrders());
        } finally {executor.shutdownNow();}
    }

    @Test void accountCreationRetryReturnsOriginalReceiptWithoutChangingPasswordOrQuota() {
        policy(parent,1,5000);
        CreateChild request=new CreateChild("retry-account","Service","Ege","Admin","admin","StrongPass123");
        CreatedChild first=service.createChild(request);
        User manager=users.findByUsernameAndCompanyId("admin",first.member().childCompanyId()).orElseThrow();
        manager.setPasswordHash(passwords.encode("ChangedPassword456"));users.saveAndFlush(manager);
        CreatedChild retry=service.createChild(request);
        assertSameAccount(first,retry);assertEquals(1,service.context().usedMembers());
        assertTrue(passwords.matches("ChangedPassword456",users.findById(manager.getId()).orElseThrow().getPasswordHash()));
        assertThrows(IllegalStateException.class,()->service.createChild(new CreateChild("retry-account","Different service","Ege","Admin","admin","StrongPass123")));
        login(outsider);policy(other,1,5000);
        CreatedChild otherReceipt=service.createChild(request);
        assertNotEquals(first.member().childCompanyId(),otherReceipt.member().childCompanyId());
    }

    @Test void concurrentAccountCreationWithSameKeyCreatesOneCompany() throws Exception {
        policy(parent,1,5000);
        CreateChild request=new CreateChild("concurrent-account","Service","Ege","Admin","admin","StrongPass123");
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            Callable<CreatedChild> create=()->{login(parentAdmin);ready.countDown();assertTrue(go.await(5,TimeUnit.SECONDS));try{return service.createChild(request);}finally{SecurityContextHolder.clearContext();}};
            Future<CreatedChild> a=executor.submit(create),b=executor.submit(create);assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            assertSameAccount(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertEquals(1,service.context().usedMembers());
        } finally {executor.shutdownNow();}
    }

    private static void assertSameAccount(CreatedChild expected,CreatedChild actual) {
        assertEquals(expected.member().id(),actual.member().id());
        assertEquals(expected.member().childCompanyId(),actual.member().childCompanyId());
        assertEquals(expected.orgCode(),actual.orgCode());
        assertEquals(expected.username(),actual.username());
    }

    @Test void memberNotificationLookupOnlyExposesTheTwoParticipants() {
        Member member=active();assertEquals(member.id(),service.getMember(member.id()).id());
        login(childAdmin);assertEquals(member.id(),service.getMember(member.id()).id());
        login(outsider);assertThrows(AccessDeniedException.class,()->service.getMember(member.id()));
    }

    private Member active(){login(parentAdmin);Member m=service.invite(new Invite(child.getOrgCode(),"Ege"));login(childAdmin);service.decideInvite(m.id(),true);login(parentAdmin);return m;}
    private Order sent(){Member m=active();return service.dispatch(request(m.id(),UUID.randomUUID().toString()));}
    private Dispatch request(Long membership,String key){return new Dispatch(membership,key,"Installation","Customer","05550000000","Service address","Internal instruction",LocalDateTime.of(2026,10,5,14,0),null);}
    private Company company(String name){Company c=new Company();c.setName(name);c.setOrgCode(UUID.randomUUID().toString().substring(0,16));c.setSubscriptionStatus("ACTIVE");return companies.saveAndFlush(c);}
    private User user(Company company,String role){return users.saveAndFlush(User.builder().companyId(company.getId()).username(UUID.randomUUID().toString()).passwordHash("not-a-login").role(role).fullName(role).build());}
    private Customer customer(Company company){Customer c=new Customer();c.setCompanyId(company.getId());c.setName("Existing");return customers.saveAndFlush(c);}
    private void policy(Company company,int maxMembers,int monthly){NetworkPolicy p=policies.findById(company.getId()).orElseGet(NetworkPolicy::new);p.setCompanyId(company.getId());p.setEnabled(true);p.setMaxMembers(maxMembers);p.setMaxMonthlyOrders(monthly);policies.saveAndFlush(p);}
    private static void login(User user){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
}
