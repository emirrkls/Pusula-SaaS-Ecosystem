package com.pusula.backend.dto;

import com.pusula.backend.entity.ServiceTicket.WorkProgressReason;
import java.time.LocalDateTime;

public class ServiceTicketRescheduleRequest {
    private LocalDateTime scheduledDate;
    private LocalDateTime scheduledEndDate;
    private WorkProgressReason reason;
    private String note;
    public LocalDateTime getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDateTime value) { scheduledDate = value; }
    public LocalDateTime getScheduledEndDate() { return scheduledEndDate; }
    public void setScheduledEndDate(LocalDateTime value) { scheduledEndDate = value; }
    public WorkProgressReason getReason() { return reason; }
    public void setReason(WorkProgressReason value) { reason = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
}
