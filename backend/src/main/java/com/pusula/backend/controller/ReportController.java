package com.pusula.backend.controller;

import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private final ServiceTicketRepository ticketRepository;
    private final UserRepository userRepository;

    public ReportController(ServiceTicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get technician performance for the last 7 days
     * Returns a map of technician name -> daily completion counts
     */
    @GetMapping("/technician-performance")
    public ResponseEntity<Map<String, Map<String, Integer>>> getTechnicianPerformance() {
        // Get completed tickets from last 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<ServiceTicket> completedTickets = ticketRepository.findCompletedTicketsSince(sevenDaysAgo);

        // DateTimeFormatter for date keys
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Group by technician and date
        Map<String, Map<String, Integer>> performanceData = new HashMap<>();

        for (ServiceTicket ticket : completedTickets) {
            if (ticket.getAssignedTechnicianId() == null || ticket.getUpdatedAt() == null) {
                continue;
            }

            // Get technician name
            User technician = userRepository.findById(ticket.getAssignedTechnicianId()).orElse(null);
            if (technician == null) {
                continue;
            }
            String techName = technician.getFullName();

            // Get date string
            String dateKey = ticket.getUpdatedAt().toLocalDate().format(dateFormatter);

            // Initialize maps if needed
            performanceData.putIfAbsent(techName, new HashMap<>());
            Map<String, Integer> dailyCounts = performanceData.get(techName);

            // Increment count
            dailyCounts.put(dateKey, dailyCounts.getOrDefault(dateKey, 0) + 1);
        }

        return ResponseEntity.ok(performanceData);
    }
}
