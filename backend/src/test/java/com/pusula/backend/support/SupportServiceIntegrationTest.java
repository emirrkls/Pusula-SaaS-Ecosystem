package com.pusula.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import com.pusula.backend.service.ReliableMutationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.sql.init.mode=never"})
@ContextConfiguration(classes=SupportServiceIntegrationTest.Config.class)
class SupportServiceIntegrationTest {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @EntityScan(basePackages={"com.pusula.backend.entity","com.pusula.backend.support"})
    @EnableJpaRepositories(basePackages={"com.pusula.backend.repository","com.pusula.backend.support"})
    @Import({SupportService.class,SupportFiles.class,SupportPrivacyService.class,ReliableMutationService.class})
    static class Config {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean SupportProperties supportProperties(){var p=new SupportProperties();p.setEnabled(true);return p;}
    }
    @Autowired SupportService service; @Autowired SupportPrivacyService privacy;
    @Autowired SupportTickets tickets; @Autowired SupportMessages messages; @Autowired SupportAttachments attachments;
    @Autowired SupportMails mails;
    @Autowired UserRepository users; @Autowired CompanyRepository companies; @Autowired SupportProperties config;
    @AfterEach void clear(){SecurityContextHolder.clearContext();com.pusula.backend.context.TenantContext.clear();config.setEnabled(true);}
    private User actor(String role) {
        var c=new Company();c.setName("İşletme");c.setSubscriptionStatus("ACTIVE");c=companies.saveAndFlush(c);
        var u=new User();u.setCompanyId(c.getId());u.setUsername(UUID.randomUUID()+"@example.invalid");u.setFullName("Test Kullanıcı");u.setPasswordHash("not-a-real-password");u.setRole(role);return users.saveAndFlush(u);
    }
    private void login(User user){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
    private SupportDtos.Create create(String key){return new SupportDtos.Create(key,"Bağlantı sorunu","BUG","İşlem tamamlanmadı.","IOS","1.0 (59)",List.of());}
    private SupportDtos.Reply reply(String body,boolean internal){return new SupportDtos.Reply(UUID.randomUUID().toString(),body,internal,null,null,List.of());}
    @Test void ownRequestsAndAttachmentsAreNotAccessibleToOtherUsersEvenInSameCompany() {
        var owner=actor("TECHNICIAN");login(owner);var detail=service.create(create(UUID.randomUUID().toString()));
        var other=actor("TECHNICIAN");other.setCompanyId(owner.getCompanyId());users.saveAndFlush(other);login(other);
        assertTrue(service.list(false,0,null,null,null,null).items().isEmpty());
        assertThrows(ResponseStatusException.class,()->service.detail(detail.ticket().id(),false,null));
        assertThrows(ResponseStatusException.class,()->service.reply(detail.ticket().id(),reply("X",false),false));
    }
    @Test void lostResponseReplayDoesNotDuplicateTicketOrMessageAndChangedPayloadConflicts() {
        var owner=actor("COMPANY_ADMIN");login(owner);String key=UUID.randomUUID().toString();
        var first=service.create(create(key));var replay=service.create(create(key));assertEquals(first.ticket().id(),replay.ticket().id());assertEquals(1,tickets.count());assertEquals(1,messages.count());assertEquals(1,mails.count());
        var response=reply("Ek bilgi",false);service.reply(first.ticket().id(),response,false);service.reply(first.ticket().id(),response,false);assertEquals(2,messages.count());
        assertThrows(ResponseStatusException.class,()->service.create(new SupportDtos.Create(key,"Değişti","BUG","Farklı","IOS","1",List.of())));
    }
    @Test void internalNotesAreInvisibleAndReadOnlyStaffCannotWrite() {
        var owner=actor("TECHNICIAN");login(owner);var ticket=service.create(create(UUID.randomUUID().toString())).ticket();
        var staff=actor("SUPER_ADMIN_OPS");login(staff);service.reply(ticket.id(),reply("YALNIZCA EKİP",true),true);service.reply(ticket.id(),reply("Kullanıcıya yanıt",false),true);
        login(owner);var detail=service.detail(ticket.id(),false,null);assertEquals(2,detail.messages().size());assertTrue(detail.messages().stream().noneMatch(m->m.body().contains("YALNIZCA")));assertTrue(detail.ticket().unread());
        service.read(ticket.id(),false);assertFalse(service.detail(ticket.id(),false,null).ticket().unread());
        var viewer=actor("SUPER_ADMIN_READONLY");login(viewer);assertEquals(3,service.detail(ticket.id(),true,null).messages().size());assertThrows(AccessDeniedException.class,()->service.reply(ticket.id(),reply("Yazma",false),true));assertThrows(AccessDeniedException.class,()->service.read(ticket.id(),true));
    }
    @Test void userCannotEscalateOrPublishInternalNoteButCanReopenResolvedTicket() {
        var owner=actor("TECHNICIAN");login(owner);var ticket=service.create(create(UUID.randomUUID().toString())).ticket();
        assertThrows(AccessDeniedException.class,()->service.reply(ticket.id(),reply("Gizli",true),false));
        login(actor("SUPER_ADMIN"));service.reply(ticket.id(),new SupportDtos.Reply(UUID.randomUUID().toString(),"Çözüldü",false,"RESOLVED",null,List.of()),true);
        login(owner);assertEquals("IN_PROGRESS",service.reply(ticket.id(),reply("Devam ediyor",false),false).ticket().status());
    }
    @Test void attachmentIsPrivateAndUserErasureRemovesWholeSupportConversation() {
        var owner=actor("TECHNICIAN");login(owner);
        String json="{\"schemaVersion\":1,\"records\":[{\"method\":\"POST\",\"status\":503,\"token\":\"SECRET\",\"route\":\"/customers/private-name\",\"errors\":[{\"domain\":\"NSURLErrorDomain\",\"code\":-1200}]}]}";
        var input=new SupportDtos.Create(UUID.randomUUID().toString(),"TLS","BUG","Bağlantı","IOS","1",List.of(new SupportDtos.Upload("application/json",Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        var detail=service.create(input);var file=detail.messages().get(0).attachments().get(0);
        assertFalse(new String(service.attachment(file.id(),false).getContent()).contains("SECRET"));
        assertFalse(new String(service.attachment(file.id(),false).getContent()).contains("private-name"));
        login(actor("TECHNICIAN"));assertThrows(ResponseStatusException.class,()->service.attachment(file.id(),false));
        privacy.eraseUser(owner.getId());assertEquals(0,tickets.count());assertEquals(0,messages.count());assertEquals(0,attachments.count());
    }
    @Test void impersonationIsDeniedAndFeatureIsClosedUntilExplicitlyEnabled() {
        login(actor("COMPANY_ADMIN"));config.setEnabled(false);assertFalse(service.contact().enabled());assertThrows(ResponseStatusException.class,()->service.list(false,0,null,null,null,null));
        config.setEnabled(true);com.pusula.backend.context.TenantContext.setReadOnlyImpersonation(true);assertThrows(AccessDeniedException.class,()->service.list(false,0,null,null,null,null));
    }
    @Test void retentionRemovesAttachmentsAfterThirtyDaysAndClosedThreadsAfter180Days() {
        var owner=actor("TECHNICIAN");login(owner);var ticket=service.create(create(UUID.randomUUID().toString())).ticket();
        var message=messages.history(ticket.id(),false,null,org.springframework.data.domain.PageRequest.of(0,1)).get(0);
        for(int days:List.of(31,29)){var file=new SupportAttachment();file.setMessageId(message.getId());file.setFileName("baglanti-raporu.json");file.setMediaType("application/json");file.setContent(new byte[]{1});file.setCreatedAt(Instant.now().minus(Duration.ofDays(days)));attachments.saveAndFlush(file);}
        privacy.retention();assertEquals(1,attachments.count());assertEquals(1,tickets.count());
        var entity=tickets.findById(ticket.id()).orElseThrow();entity.setStatus("RESOLVED");entity.setClosedAt(Instant.now().minus(Duration.ofDays(181)));tickets.saveAndFlush(entity);privacy.retention();assertEquals(0,tickets.count());
        assertEquals(0,attachments.count());assertEquals(0,messages.count());assertEquals(0,mails.count());
    }
    @Test void internalAttachmentIsNotReachableByItsIdAndInternalNoteCannotPublishStatusChange() {
        login(actor("TECHNICIAN"));var owner=(User)SecurityContextHolder.getContext().getAuthentication().getPrincipal();var ticket=service.create(create(UUID.randomUUID().toString())).ticket();
        login(actor("SUPER_ADMIN_OPS"));String report=Base64.getEncoder().encodeToString("{\"schemaVersion\":1,\"records\":[]}".getBytes());
        var result=service.reply(ticket.id(),new SupportDtos.Reply(UUID.randomUUID().toString(),"Dahili",true,null,null,List.of(new SupportDtos.Upload("application/json",report))),true);
        var attachment=result.messages().get(result.messages().size()-1).attachments().get(0);assertNotNull(service.attachment(attachment.id(),true));
        assertThrows(IllegalArgumentException.class,()->service.reply(ticket.id(),new SupportDtos.Reply(UUID.randomUUID().toString(),"Not",true,"RESOLVED",null,List.of()),true));
        login(owner);assertThrows(ResponseStatusException.class,()->service.attachment(attachment.id(),false));assertEquals(1,service.detail(ticket.id(),false,null).messages().size());
    }
    @Test void storedAttachmentBudgetBlocksOnlyNewFilesAndStillAllowsTextReplies() {
        var owner=actor("TECHNICIAN");login(owner);var detail=service.create(create(UUID.randomUUID().toString()));
        var file=new SupportAttachment();file.setMessageId(detail.messages().get(0).id());file.setFileName("test.jpg");file.setMediaType("image/jpeg");file.setContent(new byte[20*1024*1024]);file.setCreatedAt(Instant.now());attachments.saveAndFlush(file);
        String report=Base64.getEncoder().encodeToString("{\"schemaVersion\":1,\"records\":[]}".getBytes());
        assertEquals(20L*1024*1024,attachments.storedBytes(owner.getId()));
        assertThrows(IllegalArgumentException.class,()->service.reply(detail.ticket().id(),new SupportDtos.Reply(UUID.randomUUID().toString(),"Ek",false,null,null,List.of(new SupportDtos.Upload("application/json",report))),false));
        assertEquals(1,messages.count());service.reply(detail.ticket().id(),reply("Yalnızca metin",false),false);assertEquals(2,messages.count());
    }
}
