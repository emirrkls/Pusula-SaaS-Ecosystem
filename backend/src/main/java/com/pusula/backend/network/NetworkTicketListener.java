package com.pusula.backend.network;

import com.pusula.backend.entity.Notification;
import com.pusula.backend.service.AdminNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class NetworkTicketListener {
    private final NetworkOrderRepository orders;
    private final NetworkOrderEventRepository events;
    private final AdminNotificationService notifications;
    private final ZoneId zone;
    public NetworkTicketListener(NetworkOrderRepository orders, NetworkOrderEventRepository events,
            AdminNotificationService notifications, @Value("${app.business.timezone:Europe/Istanbul}") String timezone) {
        this.orders=orders; this.events=events; this.notifications=notifications; this.zone=ZoneId.of(timezone);
    }

    // Timeline + notification are committed atomically with the local ticket change.
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void changed(NetworkTicketChanged change) {
        orders.findByAcceptedTicketIdAndChildCompanyId(change.ticketId(),change.companyId()).ifPresent(order->{
            if(Objects.equals(order.getReportedTicketStatus(),change.status())
                    && Objects.equals(order.getReportedScheduledDate(),change.scheduledDate())
                    && Objects.equals(order.getReportedScheduledEndDate(),change.scheduledEndDate())) return;
            boolean initial=order.getReportedTicketStatus()==null;
            order.setReportedTicketStatus(change.status());order.setReportedScheduledDate(change.scheduledDate());
            order.setReportedScheduledEndDate(change.scheduledEndDate());orders.save(order);
            if(initial) return; // Acceptance already has its own timeline entry and notification.
            String note="Servis durumu: "+status(change.status())+" · Randevu: "+date(change.scheduledDate())
                    +(change.scheduledEndDate()==null?"":" – "+date(change.scheduledEndDate()));
            NetworkOrderEvent event=new NetworkOrderEvent();event.setOrderId(order.getId());
            event.setActorCompanyId(change.companyId());event.setActorUserId(change.actorUserId());
            event.setAction("PROGRESS");event.setNote(note);event.setCreatedAt(LocalDateTime.now(zone));events.save(event);
            notifications.notifyCompanyAdmins(order.getParentCompanyId(),"Ağ işi güncellendi","#"+order.getId()+" · "+note,
                    Notification.NotificationType.INFO,Notification.NotificationCategory.GENERAL,"NETWORK_ORDER",order.getId(),null);
        });
    }
    private String date(LocalDateTime date) { return date==null?"Belirtilmedi":date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")); }
    private String status(String status) { return switch(status) {
        case "OPEN","PENDING" -> "Atama bekliyor"; case "ASSIGNED" -> "Atandı";
        case "IN_PROGRESS" -> "İşlemde"; case "COMPLETED" -> "Tamamlandı"; case "CANCELLED" -> "İptal edildi"; default -> status;
    }; }
}
