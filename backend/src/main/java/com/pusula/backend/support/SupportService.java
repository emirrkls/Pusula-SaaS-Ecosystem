package com.pusula.backend.support;

import com.pusula.backend.context.TenantContext;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.service.ReliableMutationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class SupportService {
    private final SupportTickets tickets; private final SupportMessages messages;
    private final SupportAttachments attachments; private final SupportEvents events;
    private final SupportMails mails; private final SupportFiles files; private final SupportProperties config;
    private final UserRepository users; private final CompanyRepository companies;
    private final ReliableMutationService hashes; private final EntityManager entities;
    private static final Set<String> STATES=Set.of("NEW","IN_PROGRESS","WAITING_USER","RESOLVED","EVALUATING","PLANNED");
    public SupportService(SupportTickets tickets,SupportMessages messages,SupportAttachments attachments,
            SupportEvents events,SupportMails mails,SupportFiles files,SupportProperties config,
            UserRepository users,CompanyRepository companies,ReliableMutationService hashes,EntityManager entities) {
        this.tickets=tickets; this.messages=messages; this.attachments=attachments; this.events=events;
        this.mails=mails; this.files=files; this.config=config; this.users=users; this.companies=companies;
        this.hashes=hashes; this.entities=entities;
    }
    public SupportDtos.Contact contact() { return new SupportDtos.Contact(config.isEnabled(),config.getEmail(),config.getWebsite(),3,SupportFiles.MAX_BYTES); }
    @Transactional(readOnly=true) public long unread(boolean staff) {var user=actor(staff,false);return staff?tickets.adminUnread():tickets.unread(user.getCompanyId(),user.getId());}

    @Transactional(readOnly=true)
    public SupportDtos.Page list(boolean staff,int page,String status,String category,Long company,String search) {
        User actor=actor(staff,false);
        if(page<0 || page>10000) throw new IllegalArgumentException("Geçersiz sayfa.");
        Specification<SupportTicket> spec=(root,q,b)->b.conjunction();
        if(!staff) spec=spec.and((r,q,b)->b.and(b.equal(r.get("companyId"),actor.getCompanyId()),b.equal(r.get("userId"),actor.getId())));
        if(company!=null && staff) spec=spec.and((r,q,b)->b.equal(r.get("companyId"),company));
        if(status!=null && !status.isBlank()) { if(!STATES.contains(status)) throw new IllegalArgumentException("Geçersiz durum."); spec=spec.and((r,q,b)->b.equal(r.get("status"),status)); }
        if(category!=null && !category.isBlank()) { if(!Set.of("QUESTION","BUG","SUGGESTION").contains(category)) throw new IllegalArgumentException("Geçersiz tür."); spec=spec.and((r,q,b)->b.equal(r.get("category"),category)); }
        if(search!=null && !search.isBlank()) {
            String text=search.trim().toLowerCase(Locale.ROOT);
            if(text.length()>120) throw new IllegalArgumentException("Arama en fazla 120 karakter olabilir.");
            spec=spec.and((r,q,b)->b.like(b.lower(r.get("subject")),"%"+text.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%",'\\'));
        }
        var result=tickets.findAll(spec,PageRequest.of(page,30,Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("id"))));
        return new SupportDtos.Page(result.stream().map(t->summary(t,staff)).toList(),page,result.hasNext(),staff?tickets.adminUnread():tickets.unread(actor.getCompanyId(),actor.getId()),!staff||actor.getAuthorities().stream().anyMatch(a->"SUPPORT_WRITE".equals(a.getAuthority())));
    }
    @Transactional(readOnly=true)
    public SupportDtos.Detail detail(Long id,boolean staff,Long before) {
        var actor=actor(staff,false); var ticket=visible(id,actor,staff,false);
        return detail(ticket,staff,before);
    }
    @Transactional
    public SupportDtos.Detail create(SupportDtos.Create input) {
        User actor=actor(false,true); lockActor(actor);
        String key=key(input.clientKey()); String hash=hashes.fingerprint(input);
        var previous=messages.findByAuthorIdAndClientKey(actor.getId(),key);
        if(previous.isPresent()) return replay(previous.get(),hash,actor,false);
        if(tickets.countByUserIdAndCreatedAtAfter(actor.getId(),Instant.now().minus(Duration.ofDays(1)))>=10) throw limit();
        String subject=text(input.subject(),120); text(input.body(),4000);
        if(!Set.of("QUESTION","BUG","SUGGESTION").contains(input.category()) || !Set.of("IOS","ANDROID","DESKTOP","WEB").contains(input.platform())) throw new IllegalArgumentException("Geçersiz talep türü veya platform.");
        var ticket=new SupportTicket(); Instant now=Instant.now();
        ticket.setCompanyId(actor.getCompanyId()); ticket.setUserId(actor.getId()); ticket.setSubject(subject);
        ticket.setCategory(input.category()); ticket.setStatus("NEW"); ticket.setPriority("NORMAL"); ticket.setPlatform(input.platform());
        String version=input.appVersion()==null?"":input.appVersion();
        if(!version.matches("[0-9A-Za-z. ()_-]{0,32}")) throw new IllegalArgumentException("Geçersiz sürüm.");
        ticket.setAppVersion(version); ticket.setCreatedAt(now); ticket.setUpdatedAt(now); ticket.setLastUserAt(now); ticket.setUserReadAt(now);
        tickets.saveAndFlush(ticket);
        storeMessage(ticket,actor,key,hash,input.body(),false,false,input.attachments()); event(ticket,actor,"CREATED","NEW");
        return detail(ticket,false,null);
    }
    @Transactional
    public SupportDtos.Detail reply(Long id,SupportDtos.Reply input,boolean staff) {
        User actor=actor(staff,true); lockActor(actor);
        String key=key(input.clientKey()); String hash=hashes.fingerprint(List.of(id,input));
        var previous=messages.findByAuthorIdAndClientKey(actor.getId(),key);
        if(previous.isPresent()) return replay(previous.get(),hash,actor,staff);
        var ticket=visible(id,actor,staff,true);
        if(input.internalNote() && (input.status()!=null || input.priority()!=null)) throw new IllegalArgumentException("Dahili not ile durum/öncelik değişikliğini ayrı kaydedin.");
        if(messages.countByAuthorIdAndCreatedAtAfter(actor.getId(),Instant.now().minus(Duration.ofHours(1)))>=60) throw limit();
        if(!staff && (input.internalNote() || input.priority()!=null || input.status()!=null && !"IN_PROGRESS".equals(input.status()))) throw new AccessDeniedException("Bu işlem için yetkiniz yok.");
        if(staff && input.status()!=null) {
            if(!STATES.contains(input.status())) throw new IllegalArgumentException("Geçersiz durum.");
            if(Set.of("EVALUATING","PLANNED").contains(input.status()) && !"SUGGESTION".equals(ticket.getCategory())) throw new IllegalArgumentException("Bu durum yalnızca öneriler içindir.");
            ticket.setStatus(input.status()); ticket.setClosedAt("RESOLVED".equals(input.status())?Instant.now():null); event(ticket,actor,"STATUS",input.status());
        }
        if(staff && input.priority()!=null) {
            if(!Set.of("NORMAL","HIGH","URGENT").contains(input.priority())) throw new IllegalArgumentException("Geçersiz öncelik.");
            ticket.setPriority(input.priority()); event(ticket,actor,"PRIORITY",input.priority());
        }
        String body=input.body()==null?"":input.body().trim();
        if(body.isEmpty() && input.status()==null && input.priority()==null) throw new IllegalArgumentException("Bir mesaj yazın.");
        if(body.length()>4000) throw new IllegalArgumentException("Mesaj en fazla 4000 karakter olabilir.");
        if(!staff) { ticket.setStatus("IN_PROGRESS"); ticket.setClosedAt(null); ticket.setLastUserAt(Instant.now()); ticket.setUserReadAt(Instant.now()); }
        else if(!input.internalNote()) ticket.setLastAdminAt(Instant.now());
        if(staff) ticket.setAdminReadAt(Instant.now());
        if(body.isBlank()) body="Talep durumu/önceliği güncellendi.";
        storeMessage(ticket,actor,key,hash,body,staff,input.internalNote(),input.attachments());
        ticket.setUpdatedAt(Instant.now()); tickets.save(ticket);
        return detail(ticket,staff,null);
    }
    @Transactional
    public void read(Long id,boolean staff) {
        var actor=actor(staff,false);
        if("SUPER_ADMIN_READONLY".equals(actor.getRole()) || TenantContext.isReadOnlyImpersonation()) throw new AccessDeniedException("Salt okunur erişim.");
        var ticket=visible(id,actor,staff,true);
        if(staff) ticket.setAdminReadAt(Instant.now()); else ticket.setUserReadAt(Instant.now());
        tickets.save(ticket);
    }
    @Transactional(readOnly=true)
    public SupportAttachment attachment(Long id,boolean staff) {
        var actor=actor(staff,false); var file=attachments.findById(id).orElseThrow(SupportService::missing);
        var message=messages.findById(file.getMessageId()).orElseThrow(SupportService::missing);
        visible(message.getTicketId(),actor,staff,false);
        if(message.isInternalNote() && !staff) throw missing();
        return file;
    }
    private SupportDtos.Detail replay(SupportMessage message,String fingerprint,User actor,boolean staff) {
        if(!fingerprint.equals(message.getFingerprint())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Gönderim içeriği değişmiş. Önceki talebi kontrol edin.");
        return detail(visible(message.getTicketId(),actor,staff,false),staff,null);
    }
    private void storeMessage(SupportTicket ticket,User actor,String key,String hash,String text,boolean staff,boolean internal,List<SupportDtos.Upload> uploads) {
        if(uploads!=null && uploads.size()>3) throw new IllegalArgumentException("En fazla 3 ek seçin.");
        var prepared=uploads==null?List.<SupportFiles.SafeFile>of():uploads.stream().map(files::validate).toList();
        // Separate from operational photo quotas. A free account cannot fill the support database.
        long incoming=prepared.stream().mapToLong(file->file.bytes().length).sum();
        long allowance=(staff?100L:20L)*1024*1024;
        if(incoming>0 && attachments.storedBytes(actor.getId())+incoming>allowance) throw new IllegalArgumentException("Destek ek depolama sınırına ulaşıldı. Eki kaldırıp metin olarak gönderin.");
        var message=new SupportMessage(); message.setTicketId(ticket.getId()); message.setAuthorId(actor.getId());
        message.setBody(text); message.setStaff(staff); message.setInternalNote(internal); message.setClientKey(key); message.setFingerprint(hash); message.setCreatedAt(Instant.now());
        messages.saveAndFlush(message);
        for(var upload:prepared) {
            var file=new SupportAttachment(); file.setMessageId(message.getId()); file.setFileName(upload.name()); file.setMediaType(upload.type()); file.setContent(upload.bytes()); file.setCreatedAt(Instant.now()); attachments.save(file);
        }
        if(!staff) { var mail=new SupportMail(); mail.setTicketId(ticket.getId()); mail.setMessageId(message.getId()); mail.setNextAttemptAt(Instant.now()); mails.save(mail); }
        event(ticket,actor,internal?"INTERNAL_NOTE":staff?"STAFF_REPLY":"USER_MESSAGE","");
    }
    private SupportDtos.Detail detail(SupportTicket ticket,boolean staff,Long before) {
        var rows=messages.history(ticket.getId(),staff,before,PageRequest.of(0,101));
        Long next=rows.size()>100?rows.get(99).getId():null;
        var selected=new ArrayList<>(rows.subList(0,Math.min(100,rows.size()))); Collections.reverse(selected);
        var result=selected.stream().map(m->new SupportDtos.Message(m.getId(),m.getBody(),m.isStaff(),m.isInternalNote(),m.getCreatedAt(),attachments.metadata(m.getId()).stream().map(a->new SupportDtos.Attachment(a.getId(),a.getFileName(),a.getMediaType(),a.getSize())).toList())).toList();
        var history=events.findTop100ByTicketIdOrderByIdDesc(ticket.getId()).stream().filter(e->staff || Set.of("CREATED","STATUS").contains(e.getEventType())).map(e->new SupportDtos.Event(e.getEventType(),e.getValue(),e.getCreatedAt())).toList();
        return new SupportDtos.Detail(summary(ticket,staff),result,next,history);
    }
    private SupportDtos.Ticket summary(SupportTicket t,boolean staff) {
        boolean unread=staff?t.getLastUserAt()!=null && (t.getAdminReadAt()==null || t.getLastUserAt().isAfter(t.getAdminReadAt())):t.getLastAdminAt()!=null && (t.getUserReadAt()==null || t.getLastAdminAt().isAfter(t.getUserReadAt()));
        String company=staff?companies.findById(t.getCompanyId()).map(c->c.getName()).orElse("İşletme"):null;
        String requester=staff?users.findById(t.getUserId()).map(User::getFullName).orElse("Silinen kullanıcı"):null;
        return new SupportDtos.Ticket(t.getId(),"DST-"+String.format(Locale.ROOT,"%06d",t.getId()),t.getCompanyId(),company,t.getUserId(),requester,t.getSubject(),t.getCategory(),t.getStatus(),t.getPriority(),t.getPlatform(),t.getAppVersion(),t.getCreatedAt(),t.getUpdatedAt(),unread);
    }
    private SupportTicket visible(Long id,User actor,boolean staff,boolean lock) {
        var ticket=(lock?tickets.lockById(id):tickets.findById(id)).orElseThrow(SupportService::missing);
        if(!staff && (!Objects.equals(ticket.getUserId(),actor.getId()) || !Objects.equals(ticket.getCompanyId(),actor.getCompanyId()))) throw missing();
        return ticket;
    }
    private User actor(boolean staff,boolean write) {
        if(!config.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Destek merkezi henüz açılmadı. E-posta ile ulaşabilirsiniz.");
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !(authentication.getPrincipal() instanceof User user) || user.getId()==null || user.isDeleted()) throw new AccessDeniedException("Oturum gerekli.");
        if(TenantContext.isReadOnlyImpersonation()) throw new AccessDeniedException("Temsili oturum destek verilerine erişemez.");
        if(staff) {
            String authority=write?"SUPPORT_WRITE":"SUPPORT_READ";
            if(user.getAuthorities().stream().noneMatch(a->authority.equals(a.getAuthority()))) throw new AccessDeniedException("Destek yetkisi gerekli.");
        } else if(user.getCompanyId()==null || !Set.of("TECHNICIAN","COMPANY_ADMIN").contains(user.getRole())) throw new AccessDeniedException("İşletme oturumu gerekli.");
        return user;
    }
    private void lockActor(User actor) { if(entities.find(User.class,actor.getId(),LockModeType.PESSIMISTIC_WRITE)==null) throw new AccessDeniedException("Oturum bulunamadı."); }
    private void event(SupportTicket t,User actor,String type,String value) { var event=new SupportEvent(); event.setTicketId(t.getId()); event.setActorId(actor.getId()); event.setEventType(type); event.setValue(value); event.setCreatedAt(Instant.now()); events.save(event); }
    private static String key(String key) { if(key==null || !key.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Geçerli gönderim anahtarı gerekli."); try { return UUID.fromString(key).toString(); } catch(Exception e) { throw new IllegalArgumentException("Geçerli gönderim anahtarı gerekli."); } }
    private static String text(String text,int max) { if(text==null || text.isBlank() || text.length()>max) throw new IllegalArgumentException("Konu ve açıklama zorunludur; alan sınırlarını kontrol edin."); return text.trim(); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Destek kaydı bulunamadı."); }
    private static ResponseStatusException limit() { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Destek gönderim sınırına ulaşıldı. Bir süre sonra tekrar deneyin."); }
}
