package com.pusula.backend.network;

import com.pusula.backend.entity.*;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.repository.*;
import com.pusula.backend.service.*;
import com.pusula.backend.util.PasswordPolicy;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.pusula.backend.network.NetworkDtos.*;

@Service @Transactional
public class ServiceNetworkService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private static final List<NetworkMembership.Status> LIVE = List.of(NetworkMembership.Status.INVITED, NetworkMembership.Status.ACTIVE);
    private final NetworkPolicyRepository policies;
    private final NetworkMembershipRepository members;
    private final NetworkOrderRepository orders;
    private final NetworkOrderEventRepository events;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final CustomerRepository customers;
    private final ServiceTicketRepository tickets;
    private final ServiceTicketService ticketService;
    private final FeatureService features;
    private final PasswordEncoder passwords;
    private final AdminNotificationService notifications;
    private final AuditLogService audit;
    private final ZoneId zone;

    public ServiceNetworkService(NetworkPolicyRepository policies, NetworkMembershipRepository members,
            NetworkOrderRepository orders, NetworkOrderEventRepository events, CompanyRepository companies,
            UserRepository users, CustomerRepository customers, ServiceTicketRepository tickets,
            ServiceTicketService ticketService, FeatureService features, PasswordEncoder passwords,
            AdminNotificationService notifications, AuditLogService audit,
            @Value("${app.business.timezone:Europe/Istanbul}") String timezone) {
        this.policies=policies; this.members=members; this.orders=orders; this.events=events;
        this.companies=companies; this.users=users; this.customers=customers; this.tickets=tickets;
        this.ticketService=ticketService; this.features=features; this.passwords=passwords;
        this.notifications=notifications; this.audit=audit; this.zone=ZoneId.of(timezone);
    }

    @Transactional(readOnly=true)
    public Context context() {
        User u=admin(); Company c=company(u.getCompanyId());
        NetworkPolicy p=policies.findById(c.getId()).orElse(null);
        return new Context(c.getId(), p!=null && p.isEnabled(), writable(c), p==null?0:p.getMaxMembers(),
                members.countByParentCompanyIdAndStatusIn(c.getId(),LIVE), p==null?0:p.getMaxMonthlyOrders(), usage(c.getId()));
    }

    public void configure(Long companyId, PolicyRequest request) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication==null || !(authentication.getPrincipal() instanceof User u) || !"SUPER_ADMIN".equals(u.getRole())) throw denied();
        lockCompany(companyId);
        NetworkPolicy policy=policies.findById(companyId).orElseGet(NetworkPolicy::new);
        policy.setCompanyId(companyId); policy.setEnabled(request.enabled());
        policy.setMaxMembers(request.maxMembers()); policy.setMaxMonthlyOrders(request.maxMonthlyOrders());
        policies.save(policy);
        audit.log("UPDATE", "NETWORK_POLICY", companyId, "Servis ağı yetkisi ve limitleri güncellendi");
    }

    @Transactional(readOnly=true)
    public PageResult<Member> listMembers(int page, String query) {
        Long own=admin().getCompanyId(); String pattern=pattern(query);
        Page<NetworkMembership> result=members.findAll((root,cq,cb)-> {
            Predicate scope=cb.or(cb.equal(root.get("parentCompanyId"),own),cb.equal(root.get("childCompanyId"),own));
            if (pattern==null) return scope;
            return cb.and(scope,cb.or(cb.like(cb.lower(root.get("parentName")),pattern,'\\'),
                    cb.like(cb.lower(root.get("childName")),pattern,'\\'),cb.like(cb.lower(root.get("region")),pattern,'\\')));
        }, PageRequest.of(Math.max(0,page),50,Sort.by(Sort.Direction.DESC,"id")));
        return page(result.map(this::member));
    }

    public Member invite(Invite request) {
        User u=admin();
        Company child=companies.findByOrgCodeIgnoreCase(request.orgCode().trim()).orElseThrow(()->new IllegalArgumentException("İşletme kodu bulunamadı."));
        lockPair(u.getCompanyId(),child.getId());
        Company parent=company(u.getCompanyId()); requireWritable(parent); requireCapacity(parent.getId());
        requireAttachable(parent.getId(),child.getId());
        NetworkMembership m=newMembership(parent,child,request.region(),NetworkMembership.Status.INVITED);
        notifyCompany(child.getId(),"Servis ağı daveti",parent.getName()+" sizi servis ağına davet etti.","NETWORK_MEMBER",m.getId());
        audit.log("CREATE","NETWORK_MEMBER",m.getId(),"Alt servis daveti oluşturuldu");
        return member(m);
    }

    public CreatedChild createChild(CreateChild request) {
        User u=admin(); Company parent=lockCompany(u.getCompanyId());
        requireWritable(parent); requireCapacity(parent.getId());
        if (members.existsByChildCompanyIdAndStatusIn(parent.getId(),LIVE)) throw new IllegalStateException("Alt servis ikinci kademe ağ açamaz.");
        PasswordPolicy.requireStrong(request.password());
        if(request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Şifre en fazla 72 UTF-8 bayt olabilir.");
        Company child=new Company(); child.setName(request.name().trim());
        child.setOrgCode("SN"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT));
        child.setPlanType(PlanType.CIRAK); child.setSubscriptionStatus("TRIAL"); child.setTrialEndsAt(now().plusDays(14));
        companies.saveAndFlush(child);
        User manager=User.builder().companyId(child.getId()).username(request.username().trim())
                .fullName(request.adminName().trim()).passwordHash(passwords.encode(request.password())).role("COMPANY_ADMIN").build();
        users.save(manager);
        NetworkMembership m=newMembership(parent,child,request.region(),NetworkMembership.Status.ACTIVE);
        audit.log("CREATE","NETWORK_MEMBER",m.getId(),"Ayrı işletme ve alt servis yöneticisi oluşturuldu");
        return new CreatedChild(member(m),child.getOrgCode(),manager.getUsername());
    }

    public Member decideInvite(Long id, boolean accept) {
        User u=admin(); NetworkMembership m=lockMemberPair(id,u.getCompanyId());
        if(!m.getChildCompanyId().equals(u.getCompanyId())) throw denied();
        requireWritable(company(u.getCompanyId()));
        if(m.getStatus()==(accept?NetworkMembership.Status.ACTIVE:NetworkMembership.Status.DECLINED)) return member(m);
        if(m.getStatus()!=NetworkMembership.Status.INVITED) throw new IllegalStateException("Davet artık beklemiyor.");
        if(accept) {
            NetworkPolicy p=requirePolicy(m.getParentCompanyId());
            requireWritable(company(m.getParentCompanyId()));
            if(members.countByParentCompanyIdAndStatusIn(m.getParentCompanyId(),LIVE)>p.getMaxMembers()) throw new IllegalStateException("Ana firmanın alt servis limiti dolu.");
        }
        m.setStatus(accept?NetworkMembership.Status.ACTIVE:NetworkMembership.Status.DECLINED);m.setUpdatedAt(now());
        members.save(m);
        notifyCompany(m.getParentCompanyId(),"Servis ağı daveti yanıtlandı",m.getChildName()+ (accept?" daveti kabul etti.":" daveti reddetti."),"NETWORK_MEMBER",id);
        audit.log("UPDATE","NETWORK_MEMBER",id,accept?"Davet kabul edildi":"Davet reddedildi");
        return member(m);
    }

    public Member closeMember(Long id, Note note) {
        Long own=admin().getCompanyId(); NetworkMembership m=lockMemberPair(id,own);
        requireWritable(company(own));
        if(m.getStatus()==NetworkMembership.Status.CLOSED) return member(m);
        if(m.getStatus()==NetworkMembership.Status.DECLINED) throw new IllegalStateException("Davet zaten reddedilmiş.");
        List<NetworkOrder> live=orders.findByMembershipIdAndStatusIn(id,List.of(NetworkOrder.Status.SENT,NetworkOrder.Status.ACCEPTED));
        Map<Long,ServiceTicket> linked=linkedTickets(live);
        if(live.stream().anyMatch(o->o.getStatus()==NetworkOrder.Status.SENT || !terminal(linked.get(o.getAcceptedTicketId()))))
            throw new IllegalStateException("Bekleyen veya açık ağ işleri varken bağlantı kapatılamaz. Önce işleri sonuçlandırın.");
        m.setStatus(NetworkMembership.Status.CLOSED);m.setUpdatedAt(now()); members.save(m);
        audit.log("UPDATE","NETWORK_MEMBER",id,"Servis ağı bağlantısı kapatıldı: "+note.note().trim());
        notifyCompany(other(m,own),"Servis ağı bağlantısı kapatıldı",note.note().trim(),"NETWORK_MEMBER",id);
        return member(m);
    }

    public Order dispatch(Dispatch request) {
        User u=admin(); NetworkMembership m=lockMemberPair(request.membershipId(),u.getCompanyId());
        if(!m.getParentCompanyId().equals(u.getCompanyId())) throw denied();
        requireWritable(company(u.getCompanyId())); requireWritable(company(m.getChildCompanyId()));
        Optional<NetworkOrder> previous=orders.findByParentCompanyIdAndRequestKey(u.getCompanyId(),request.requestKey().trim());
        if(previous.isPresent()) {
            NetworkOrder o=previous.get();
            if(!sameRequest(o,request)) throw new IllegalStateException("Bu gönderim anahtarı farklı bir iş için kullanılmış.");
            return order(o,linkedTickets(List.of(o)));
        }
        if(m.getStatus()!=NetworkMembership.Status.ACTIVE) throw new IllegalStateException("Alt servis bağlantısı aktif değil.");
        NetworkPolicy p=requirePolicy(u.getCompanyId());
        if(usage(u.getCompanyId())>=p.getMaxMonthlyOrders()) throw new IllegalStateException("Aylık ağ iş emri limiti dolu.");
        if(request.scheduledEndDate()!=null && !request.scheduledEndDate().isAfter(request.scheduledDate())) throw new IllegalArgumentException("Bitiş saati başlangıçtan sonra olmalıdır.");
        NetworkOrder o=new NetworkOrder();o.setMembershipId(m.getId());o.setParentCompanyId(m.getParentCompanyId());o.setChildCompanyId(m.getChildCompanyId());
        o.setParentName(m.getParentName());o.setChildName(m.getChildName());o.setRequestKey(request.requestKey().trim());
        o.setTitle(request.title().trim());o.setCustomerName(request.customerName().trim());o.setCustomerPhone(trim(request.customerPhone()));
        o.setCustomerAddress(trim(request.customerAddress()));o.setInstruction(trim(request.instruction()));
        o.setScheduledDate(request.scheduledDate());o.setScheduledEndDate(request.scheduledEndDate());o.setStatus(NetworkOrder.Status.SENT);o.setCreatedAt(now());
        orders.saveAndFlush(o);event(o,u,"SENT","İş alt servise gönderildi.");
        notifyCompany(o.getChildCompanyId(),"Yeni ağ iş emri",o.getParentName()+" · #"+o.getId()+" · "+o.getTitle(),"NETWORK_ORDER",o.getId());
        return order(o,Map.of());
    }

    public Order accept(Long id, Accept request) {
        User u=admin(); NetworkOrder o=lockOrderPair(id,u.getCompanyId());
        if(!o.getChildCompanyId().equals(u.getCompanyId())) throw denied();
        requireWritable(company(u.getCompanyId()));
        if(o.getStatus()==NetworkOrder.Status.ACCEPTED) return order(o,linkedTickets(List.of(o)));
        requireSent(o);
        NetworkMembership m=members.findById(o.getMembershipId()).orElseThrow();
        if(m.getStatus()!=NetworkMembership.Status.ACTIVE) throw new IllegalStateException("Bağlantı aktif değil.");
        features.checkQuota(u.getCompanyId(),"TICKETS");
        if(request.technicianId()!=null) users.findByIdAndCompanyId(request.technicianId(),u.getCompanyId())
                .filter(technician->"TECHNICIAN".equals(technician.getRole())).orElseThrow(ServiceNetworkService::denied);
        Long customerId=request.customerId();
        if(customerId!=null) {
            customers.findByIdAndCompanyId(customerId,u.getCompanyId()).orElseThrow(ServiceNetworkService::denied);
        } else {
            features.checkQuota(u.getCompanyId(),"CUSTOMERS");
            Customer customer=new Customer();customer.setCompanyId(u.getCompanyId());customer.setName(o.getCustomerName());
            customer.setPhone(o.getCustomerPhone());customer.setAddress(o.getCustomerAddress());
            customerId=customers.saveAndFlush(customer).getId();
        }
        ServiceTicketDTO dto=new ServiceTicketDTO(); dto.setCustomerId(customerId);dto.setAssignedTechnicianId(request.technicianId());
        dto.setDescription(o.getTitle());dto.setScheduledDate(o.getScheduledDate());dto.setScheduledEndDate(o.getScheduledEndDate());
        dto.setTechnicianPrivateNote("Servis ağı işi #"+o.getId()+" · "+o.getParentName()+"\n"+o.getInstruction());
        ServiceTicketDTO ticket=ticketService.createTicket(dto);
        o.setAcceptedTicketId(ticket.getId());o.setStatus(NetworkOrder.Status.ACCEPTED);o.setUpdatedAt(now());orders.save(o);
        event(o,u,"ACCEPTED","Alt serviste servis fişi oluşturuldu.");
        notifyCompany(o.getParentCompanyId(),"Ağ işi kabul edildi",o.getChildName()+" · #"+o.getId(),"NETWORK_ORDER",o.getId());
        return order(o,linkedTickets(List.of(o)));
    }

    public Order resolve(Long id, Note note, boolean cancel) {
        User u=admin();NetworkOrder o=lockOrderPair(id,u.getCompanyId());
        if(!(cancel?o.getParentCompanyId():o.getChildCompanyId()).equals(u.getCompanyId())) throw denied();
        requireWritable(company(u.getCompanyId()));
        NetworkOrder.Status target=cancel?NetworkOrder.Status.CANCELLED:NetworkOrder.Status.REJECTED;
        if(o.getStatus()==target) return order(o,Map.of());
        requireSent(o);o.setStatus(target);o.setResolutionNote(note.note().trim());o.setUpdatedAt(now());orders.save(o);
        event(o,u,target.name(),o.getResolutionNote());
        notifyCompany(cancel?o.getChildCompanyId():o.getParentCompanyId(),cancel?"Ağ işi geri çekildi":"Ağ işi reddedildi",
                "#"+o.getId()+" · "+o.getResolutionNote(),"NETWORK_ORDER",id);
        return order(o,Map.of());
    }

    public void addNote(Long id, Note note) {
        User u=admin(); NetworkOrder o=lockOrderPair(id,u.getCompanyId());requireWritable(company(u.getCompanyId()));
        event(o,u,"NOTE",note.note().trim());
        notifyCompany(o.getParentCompanyId().equals(u.getCompanyId())?o.getChildCompanyId():o.getParentCompanyId(),
                "Ağ işine not eklendi","#"+id+" · "+note.note().trim(),"NETWORK_ORDER",id);
    }

    @Transactional(readOnly=true)
    public PageResult<Order> listOrders(String direction, String query, NetworkOrder.Status status, LocalDate from, LocalDate to, int page) {
        Long own=admin().getCompanyId();String pattern=pattern(query);
        if(!List.of("incoming","outgoing").contains(direction)) throw new IllegalArgumentException("Geçersiz iş yönü.");
        if(from!=null && to!=null && from.isAfter(to)) throw new IllegalArgumentException("Tarih aralığı geçersiz.");
        Page<NetworkOrder> result=orders.findAll((root,cq,cb)-> {
            List<Predicate> filters=new ArrayList<>();
            filters.add(cb.equal(root.get(direction.equals("incoming")?"childCompanyId":"parentCompanyId"),own));
            if(status!=null) filters.add(cb.equal(root.get("status"),status));
            if(from!=null) filters.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"),from.atStartOfDay()));
            if(to!=null) filters.add(cb.lessThan(root.get("scheduledDate"),to.plusDays(1).atStartOfDay()));
            if(pattern!=null) filters.add(cb.or(cb.like(cb.lower(root.get("title")),pattern,'\\'),cb.like(cb.lower(root.get("customerName")),pattern,'\\'),
                    cb.like(cb.lower(root.get("parentName")),pattern,'\\'),cb.like(cb.lower(root.get("childName")),pattern,'\\'),cb.like(root.get("id").as(String.class),pattern,'\\')));
            return cb.and(filters.toArray(Predicate[]::new));
        },PageRequest.of(Math.max(0,page),50,Sort.by(Sort.Direction.DESC,"scheduledDate","id")));
        Map<Long,ServiceTicket> linked=linkedTickets(result.getContent());
        return page(result.map(o->order(o,linked)));
    }

    @Transactional(readOnly=true)
    public Order getOrder(Long id) { NetworkOrder o=visibleOrder(id,admin().getCompanyId());return order(o,linkedTickets(List.of(o))); }
    @Transactional(readOnly=true)
    public PageResult<Event> history(Long id,int page) {
        visibleOrder(id,admin().getCompanyId());
        return page(events.findByOrderIdOrderByIdDesc(id,PageRequest.of(Math.max(0,page),50)).map(e->new Event(e.getId(),e.getAction(),e.getNote(),e.getActorCompanyId(),e.getCreatedAt())));
    }

    private User admin() {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null || !(a.getPrincipal() instanceof User u) || !("COMPANY_ADMIN".equals(u.getRole()) || "SUPER_ADMIN".equals(u.getRole())) || u.getCompanyId()==null) throw denied();
        return u;
    }
    private Company company(Long id) { return companies.findById(id).orElseThrow(ServiceNetworkService::denied); }
    private Company lockCompany(Long id) { return companies.lockById(id).orElseThrow(ServiceNetworkService::denied); }
    private void lockPair(Long a,Long b) { if(a.equals(b)) throw new IllegalArgumentException("İşletme kendisine bağlanamaz.");lockCompany(Math.min(a,b));lockCompany(Math.max(a,b)); }
    private NetworkMembership lockMemberPair(Long id,Long own) {
        NetworkMembership m=members.findById(id).filter(x->x.getParentCompanyId().equals(own)||x.getChildCompanyId().equals(own)).orElseThrow(ServiceNetworkService::denied);
        lockPair(m.getParentCompanyId(),m.getChildCompanyId());
        entityManager.refresh(m,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return m;
    }
    private NetworkOrder lockOrderPair(Long id,Long own) {
        NetworkOrder o=visibleOrder(id,own);lockPair(o.getParentCompanyId(),o.getChildCompanyId());
        entityManager.refresh(o,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return o;
    }
    private NetworkOrder visibleOrder(Long id,Long own) { return orders.findById(id).filter(o->o.getParentCompanyId().equals(own)||o.getChildCompanyId().equals(own)).orElseThrow(ServiceNetworkService::denied); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Servis ağı kaydına erişim yetkiniz yok."); }
    private boolean writable(Company c) {
        if(Boolean.TRUE.equals(c.getIsReadOnly()) || "SUSPENDED".equals(c.getSubscriptionStatus())) return false;
        if("TRIAL".equals(c.getSubscriptionStatus()) && c.getTrialEndsAt()!=null && c.getTrialEndsAt().isBefore(now())) return false;
        return c.getSubscriptionExpiresAt()==null || !c.getSubscriptionExpiresAt().isBefore(now());
    }
    private void requireWritable(Company c) { if(!writable(c)) throw new IllegalStateException("İşletme hesabı salt okunur; işlem yapılamaz."); }
    private NetworkPolicy requirePolicy(Long id) { return policies.findById(id).filter(NetworkPolicy::isEnabled).orElseThrow(()->new IllegalStateException("Servis ağı yönetimi bu işletme için etkin değil.")); }
    private void requireCapacity(Long id) {
        NetworkPolicy p=requirePolicy(id);
        if(members.countByParentCompanyIdAndStatusIn(id,LIVE)>=p.getMaxMembers()) throw new IllegalStateException("Alt servis limiti dolu; bekleyen davetler de limite dahildir.");
    }
    private void requireAttachable(Long parent,Long child) {
        if(members.existsByChildCompanyIdAndStatusIn(parent,LIVE) || members.existsByChildCompanyIdAndStatusIn(child,LIVE)
                || members.countByParentCompanyIdAndStatusIn(child,LIVE)>0) throw new IllegalStateException("İşletme zaten bir ağa bağlı veya kendi ağı var. İlk sürüm tek kademeli servis ağını destekler.");
    }
    private NetworkMembership newMembership(Company parent,Company child,String region,NetworkMembership.Status status) {
        NetworkMembership m=new NetworkMembership();m.setParentCompanyId(parent.getId());m.setChildCompanyId(child.getId());
        m.setParentName(parent.getName());m.setChildName(child.getName());m.setRegion(trim(region));m.setStatus(status);m.setCreatedAt(now());return members.saveAndFlush(m);
    }
    private long usage(Long id) { LocalDateTime start=now().toLocalDate().withDayOfMonth(1).atStartOfDay();return orders.countByParentCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(id,start,start.plusMonths(1)); }
    private LocalDateTime now() { return LocalDateTime.now(zone); }
    private void requireSent(NetworkOrder o) { if(o.getStatus()!=NetworkOrder.Status.SENT) throw new IllegalStateException("Yalnızca kabul bekleyen iş değiştirilebilir. Kabul edilen işi alt servis kendi fişinden yönetir."); }
    private Long other(NetworkMembership m,Long own) { return m.getParentCompanyId().equals(own)?m.getChildCompanyId():m.getParentCompanyId(); }
    private static String trim(String value) { return value==null?"":value.trim(); }
    private static String pattern(String q) { return q==null||q.isBlank()?null:"%"+q.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
    private Member member(NetworkMembership m) { return new Member(m.getId(),m.getParentCompanyId(),m.getChildCompanyId(),m.getParentName(),m.getChildName(),m.getRegion(),m.getStatus().name(),m.getCreatedAt()); }
    private Map<Long,ServiceTicket> linkedTickets(List<NetworkOrder> list) {
        Set<Long> ids=list.stream().map(NetworkOrder::getAcceptedTicketId).filter(Objects::nonNull).collect(Collectors.toSet());
        if(ids.isEmpty()) return Map.of();
        return tickets.findAllById(ids).stream().collect(Collectors.toMap(ServiceTicket::getId,t->t));
    }
    private static boolean terminal(ServiceTicket t) { return t!=null && (t.getStatus()==ServiceTicket.TicketStatus.COMPLETED || t.getStatus()==ServiceTicket.TicketStatus.CANCELLED); }
    private Order order(NetworkOrder o,Map<Long,ServiceTicket> linked) {
        ServiceTicket t=o.getAcceptedTicketId()==null?null:linked.get(o.getAcceptedTicketId());
        if(t!=null && !o.getChildCompanyId().equals(t.getCompanyId())) t=null;
        return new Order(o.getId(),o.getMembershipId(),o.getParentCompanyId(),o.getChildCompanyId(),o.getParentName(),o.getChildName(),o.getTitle(),
                o.getCustomerName(),o.getCustomerPhone(),o.getCustomerAddress(),o.getInstruction(),o.getScheduledDate(),o.getScheduledEndDate(),o.getStatus().name(),
                o.getAcceptedTicketId(),t==null?null:t.getStatus().name(),t==null?null:t.getScheduledDate(),t==null?null:t.getScheduledEndDate(),t==null?null:t.getCompletedAt(),o.getResolutionNote(),o.getCreatedAt());
    }
    private static <T> PageResult<T> page(Page<T> p) { return new PageResult<>(p.getContent(),p.getTotalElements(),p.getNumber(),p.getTotalPages(),p.hasNext()); }
    private void notifyCompany(Long company,String title,String body,String reference,Long id) {
        notifications.notifyCompanyAdmins(company,title,body.length()>500?body.substring(0,500):body,Notification.NotificationType.INFO,Notification.NotificationCategory.GENERAL,reference,id,null);
    }
    private void event(NetworkOrder o,User u,String action,String note) {
        NetworkOrderEvent e=new NetworkOrderEvent();e.setOrderId(o.getId());e.setActorCompanyId(u.getCompanyId());e.setActorUserId(u.getId());
        e.setAction(action);e.setNote(note);e.setCreatedAt(now());events.save(e);
        audit.log(action,"NETWORK_ORDER",o.getId(),note);
    }
    private boolean sameRequest(NetworkOrder o,Dispatch r) {
        return o.getMembershipId().equals(r.membershipId()) && o.getTitle().equals(r.title().trim()) && o.getCustomerName().equals(r.customerName().trim())
                && Objects.equals(o.getCustomerPhone(),trim(r.customerPhone())) && Objects.equals(o.getCustomerAddress(),trim(r.customerAddress()))
                && Objects.equals(o.getInstruction(),trim(r.instruction())) && o.getScheduledDate().equals(r.scheduledDate()) && Objects.equals(o.getScheduledEndDate(),r.scheduledEndDate());
    }
}
