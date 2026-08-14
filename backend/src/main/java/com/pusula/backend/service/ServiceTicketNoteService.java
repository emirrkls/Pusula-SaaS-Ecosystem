package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketNoteDTO;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceTicketNote;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.ServiceTicketNoteRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceTicketNoteService {
    private final ServiceTicketNoteRepository noteRepository;
    private final ServiceTicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public ServiceTicketNoteService(ServiceTicketNoteRepository noteRepository,
            ServiceTicketRepository ticketRepository, AuditLogService auditLogService) {
        this.noteRepository = noteRepository;
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ServiceTicketNoteDTO> getNotes(Long ticketId) {
        User user = currentUser();
        ServiceTicket ticket = requireVisibleTicket(ticketId, user);
        return noteRepository.findByServiceTicketIdAndCompanyIdOrderByCreatedAtAsc(ticket.getId(), user.getCompanyId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceTicketNoteDTO addWorkLog(Long ticketId, String content) {
        User user = currentUser();
        ServiceTicket ticket = requireVisibleTicket(ticketId, user);
        if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || ticket.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Kapali servis fisine teknisyen notu eklenemez.");
        }
        return save(ticket, user, content, ServiceTicketNote.NoteType.WORK_LOG);
    }

    @Transactional
    public ServiceTicketNoteDTO addClosureNote(Long ticketId, String content) {
        User user = currentUser();
        ServiceTicket ticket = requireVisibleTicket(ticketId, user);
        return save(ticket, user, content, ServiceTicketNote.NoteType.CLOSURE);
    }

    private ServiceTicketNoteDTO save(ServiceTicket ticket, User user, String content,
            ServiceTicketNote.NoteType type) {
        String normalized = content != null ? content.trim() : "";
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Teknisyen notu bos olamaz.");
        }
        ServiceTicketNote note = new ServiceTicketNote();
        note.setCompanyId(user.getCompanyId());
        note.setServiceTicketId(ticket.getId());
        note.setAuthorUserId(user.getId());
        note.setAuthorName(user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName() : user.getUsername());
        note.setNoteType(type);
        note.setContent(normalized);
        ServiceTicketNote saved = noteRepository.save(note);
        auditLogService.log("NOTE", "TICKET", ticket.getId(),
                type == ServiceTicketNote.NoteType.CLOSURE ? "Kapanis teknisyen notu eklendi" : "Teknisyen notu eklendi");
        return toDto(saved);
    }

    private ServiceTicket requireVisibleTicket(Long ticketId, User user) {
        ServiceTicket ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new IllegalArgumentException("Servis fisi bulunamadi."));
        if ("TECHNICIAN".equals(user.getRole())
                && (ticket.getAssignedTechnicianId() == null
                        || !ticket.getAssignedTechnicianId().equals(user.getId()))) {
            throw new AccessDeniedException("Yalnizca size atanmis servisin notlarini gorebilirsiniz.");
        }
        return ticket;
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private ServiceTicketNoteDTO toDto(ServiceTicketNote note) {
        ServiceTicketNoteDTO dto = new ServiceTicketNoteDTO();
        dto.setId(note.getId());
        dto.setServiceTicketId(note.getServiceTicketId());
        dto.setAuthorUserId(note.getAuthorUserId());
        dto.setAuthorName(note.getAuthorName());
        dto.setNoteType(note.getNoteType().name());
        dto.setContent(note.getContent());
        dto.setCreatedAt(note.getCreatedAt());
        return dto;
    }
}
