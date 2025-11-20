package com.pusula.backend.service;

import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceTicketService {

    private final ServiceTicketRepository repository;
    private final CustomerRepository customerRepository;

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public List<ServiceTicketDTO> getAllTickets() {
        User user = getCurrentUser();
        return repository.findByCompanyId(user.getCompanyId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ServiceTicketDTO createTicket(ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(user.getCompanyId())
                .customerId(dto.getCustomerId())
                .assignedTechnicianId(dto.getAssignedTechnicianId())
                .status(ServiceTicket.TicketStatus.PENDING)
                .scheduledDate(dto.getScheduledDate())
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .build();

        if (dto.getStatus() != null) {
            ticket.setStatus(dto.getStatus());
        }

        ServiceTicket saved = repository.save(ticket);
        return mapToDTO(saved);
    }

    public ServiceTicketDTO updateTicket(UUID id, ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(id)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if (dto.getStatus() != null)
            ticket.setStatus(dto.getStatus());
        if (dto.getAssignedTechnicianId() != null)
            ticket.setAssignedTechnicianId(dto.getAssignedTechnicianId());
        if (dto.getScheduledDate() != null)
            ticket.setScheduledDate(dto.getScheduledDate());
        if (dto.getNotes() != null)
            ticket.setNotes(dto.getNotes());

        ServiceTicket saved = repository.save(ticket);
        return mapToDTO(saved);
    }

    public ServiceTicketDTO createPublicTicket(PublicServiceRequestDTO dto) {
        // 1. Find or Create Customer
        Customer customer = customerRepository.findByCompanyId(dto.getCompanyId()).stream()
                .filter(c -> c.getPhone() != null && c.getPhone().equals(dto.getCustomerPhone()))
                .findFirst()
                .orElseGet(() -> {
                    Customer newCustomer = Customer.builder()
                            .companyId(dto.getCompanyId())
                            .name(dto.getCustomerName())
                            .phone(dto.getCustomerPhone())
                            .address(dto.getCustomerAddress())
                            .build();
                    return customerRepository.save(newCustomer);
                });

        // 2. Create Ticket
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(dto.getCompanyId())
                .customerId(customer.getId())
                .status(ServiceTicket.TicketStatus.PENDING)
                .description(dto.getDescription())
                .build();

        ServiceTicket saved = repository.save(ticket);
        return mapToDTO(saved);
    }

    private ServiceTicketDTO mapToDTO(ServiceTicket ticket) {
        return ServiceTicketDTO.builder()
                .id(ticket.getId())
                .customerId(ticket.getCustomerId())
                .assignedTechnicianId(ticket.getAssignedTechnicianId())
                .status(ticket.getStatus())
                .scheduledDate(ticket.getScheduledDate())
                .description(ticket.getDescription())
                .notes(ticket.getNotes())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
