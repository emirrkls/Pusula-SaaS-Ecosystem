package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin dashboard service — aggregates KPIs, technician stats,
 * profit analysis, quota status, and field radar data.
 */
@Service
public class AdminDashboardService {

    private final ServiceTicketRepository ticketRepository;
    private final CurrentAccountRepository currentAccountRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final ServiceUsedPartRepository usedPartRepository;
    private final CustomerRepository customerRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final UsageTrackingRepository usageTrackingRepository;

    public AdminDashboardService(ServiceTicketRepository ticketRepository,
                                  CurrentAccountRepository currentAccountRepository,
                                  ExpenseRepository expenseRepository,
                                  UserRepository userRepository,
                                  InventoryRepository inventoryRepository,
                                  ServiceUsedPartRepository usedPartRepository,
                                  CustomerRepository customerRepository,
                                  PlanFeatureRepository planFeatureRepository,
                                  UsageTrackingRepository usageTrackingRepository) {
        this.ticketRepository = ticketRepository;
        this.currentAccountRepository = currentAccountRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.usedPartRepository = usedPartRepository;
        this.customerRepository = customerRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.usageTrackingRepository = usageTrackingRepository;
    }

    // ═══════════════════════════════════════════════════
    //  1. DASHBOARD KPIs
    // ═══════════════════════════════════════════════════

    @Data
    @Builder
    public static class DashboardKPIs {
        private BigDecimal monthlyRevenue;
        private BigDecimal outstandingDebt;
        private BigDecimal netProfit;
        private BigDecimal profitMargin; // percentage
        private int activeTickets;
        private int completedThisMonth;
        private BigDecimal inventoryValue;
    }

    public DashboardKPIs getDashboardKPIs(Long companyId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDateTime monthStartDT = monthStart.atStartOfDay();

        List<ServiceTicket> tickets = ticketRepository.findByCompanyId(companyId);

        // Monthly revenue
        BigDecimal monthlyRevenue = tickets.stream()
                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(monthStartDT))
                .map(t -> t.getCollectedAmount() != null ? t.getCollectedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Monthly expenses
        BigDecimal monthlyExpenses = expenseRepository
                .findByCompanyIdAndDateBetween(companyId, monthStart, LocalDate.now())
                .stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit = monthlyRevenue.subtract(monthlyExpenses);

        // Profit margin
        BigDecimal profitMargin = BigDecimal.ZERO;
        if (monthlyRevenue.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netProfit.multiply(BigDecimal.valueOf(100))
                    .divide(monthlyRevenue, 1, RoundingMode.HALF_UP);
        }

        // Outstanding debt
        BigDecimal outstandingDebt = currentAccountRepository.findByCompanyId(companyId).stream()
                .map(ca -> ca.getBalance() != null ? ca.getBalance() : BigDecimal.ZERO)
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Active tickets
        int activeTickets = (int) tickets.stream()
                .filter(t -> t.getStatus() != ServiceTicket.TicketStatus.COMPLETED
                        && t.getStatus() != ServiceTicket.TicketStatus.CANCELLED)
                .count();

        int completedThisMonth = (int) tickets.stream()
                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(monthStartDT))
                .count();

        // Inventory value
        BigDecimal inventoryValue = inventoryRepository.findByCompanyId(companyId).stream()
                .filter(i -> i.getBuyPrice() != null && i.getQuantity() != null)
                .map(i -> i.getBuyPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardKPIs.builder()
                .monthlyRevenue(monthlyRevenue)
                .outstandingDebt(outstandingDebt)
                .netProfit(netProfit)
                .profitMargin(profitMargin)
                .activeTickets(activeTickets)
                .completedThisMonth(completedThisMonth)
                .inventoryValue(inventoryValue)
                .build();
    }

    // ═══════════════════════════════════════════════════
    //  2. TECHNICIAN STATS
    // ═══════════════════════════════════════════════════

    @Data
    @Builder
    public static class TechnicianStatDTO {
        private Long userId;
        private String fullName;
        private int completedToday;
        private int completedThisMonth;
        private BigDecimal collectedToday;
        private BigDecimal collectedThisMonth;
        private int activeTickets;
        private String lastLocation; // coordinates from last completed ticket's customer
    }

    public List<TechnicianStatDTO> getTechnicianStats(Long companyId) {
        List<User> technicians = userRepository.findByCompanyId(companyId).stream()
                .filter(u -> "TECHNICIAN".equals(u.getRole()))
                .collect(Collectors.toList());

        List<ServiceTicket> allTickets = ticketRepository.findByCompanyId(companyId);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return technicians.stream().map(tech -> {
            List<ServiceTicket> myTickets = allTickets.stream()
                    .filter(t -> tech.getId().equals(t.getAssignedTechnicianId()))
                    .collect(Collectors.toList());

            // Completed today
            long completedToday = myTickets.stream()
                    .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                    .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(todayStart))
                    .count();

            // Completed this month
            long completedMonth = myTickets.stream()
                    .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                    .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(monthStart))
                    .count();

            // Collections today
            BigDecimal collectedToday = myTickets.stream()
                    .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                    .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(todayStart))
                    .map(t -> t.getCollectedAmount() != null ? t.getCollectedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Collections this month
            BigDecimal collectedMonth = myTickets.stream()
                    .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                    .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(monthStart))
                    .map(t -> t.getCollectedAmount() != null ? t.getCollectedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Active tickets
            int active = (int) myTickets.stream()
                    .filter(t -> t.getStatus() != ServiceTicket.TicketStatus.COMPLETED
                            && t.getStatus() != ServiceTicket.TicketStatus.CANCELLED)
                    .count();

            // Last location — from last completed ticket's customer
            String lastLocation = myTickets.stream()
                    .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                    .max(Comparator.comparing(t -> t.getUpdatedAt() != null ? t.getUpdatedAt() : LocalDateTime.MIN))
                    .flatMap(t -> customerRepository.findById(t.getCustomerId()))
                    .map(Customer::getCoordinates)
                    .orElse(null);

            return TechnicianStatDTO.builder()
                    .userId(tech.getId())
                    .fullName(tech.getFullName())
                    .completedToday((int) completedToday)
                    .completedThisMonth((int) completedMonth)
                    .collectedToday(collectedToday)
                    .collectedThisMonth(collectedMonth)
                    .activeTickets(active)
                    .lastLocation(lastLocation)
                    .build();
        }).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════
    //  3. PROFIT ANALYSIS
    // ═══════════════════════════════════════════════════

    @Data
    @Builder
    public static class ProfitAnalysis {
        private BigDecimal totalCostOfGoodsSold;
        private BigDecimal totalRevenueFromParts;
        private BigDecimal grossProfit;
        private BigDecimal grossMarginPercent;
        private List<PartProfitDTO> topProfitableParts;
    }

    @Data
    @Builder
    public static class PartProfitDTO {
        private String partName;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private int quantitySold;
        private BigDecimal totalProfit;
        private BigDecimal marginPercent;
    }

    public ProfitAnalysis getProfitAnalysis(Long companyId) {
        // Get all used parts from completed tickets
        List<ServiceTicket> completedTickets = ticketRepository.findByCompanyId(companyId).stream()
                .filter(t -> ServiceTicket.TicketStatus.COMPLETED.equals(t.getStatus()))
                .collect(Collectors.toList());

        BigDecimal totalCOGS = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        Map<Long, PartProfitAccumulator> partProfits = new HashMap<>();

        for (ServiceTicket ticket : completedTickets) {
            List<ServiceUsedPart> parts = usedPartRepository.findByServiceTicketId(ticket.getId());
            for (ServiceUsedPart part : parts) {
                Inventory inv = part.getInventory();
                if (inv == null) continue;

                BigDecimal buyPrice = inv.getBuyPrice() != null ? inv.getBuyPrice() : BigDecimal.ZERO;
                BigDecimal sellPrice = part.getSellingPriceSnapshot() != null
                        ? part.getSellingPriceSnapshot() : BigDecimal.ZERO;
                int qty = part.getQuantityUsed();

                BigDecimal cost = buyPrice.multiply(BigDecimal.valueOf(qty));
                BigDecimal revenue = sellPrice.multiply(BigDecimal.valueOf(qty));

                totalCOGS = totalCOGS.add(cost);
                totalRevenue = totalRevenue.add(revenue);

                partProfits.computeIfAbsent(inv.getId(), k -> new PartProfitAccumulator(inv.getPartName(), buyPrice, sellPrice))
                        .addSale(qty, revenue.subtract(cost));
            }
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCOGS);
        BigDecimal grossMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Top 10 most profitable parts
        List<PartProfitDTO> topParts = partProfits.values().stream()
                .sorted((a, b) -> b.totalProfit.compareTo(a.totalProfit))
                .limit(10)
                .map(acc -> PartProfitDTO.builder()
                        .partName(acc.partName)
                        .buyPrice(acc.buyPrice)
                        .sellPrice(acc.sellPrice)
                        .quantitySold(acc.totalQty)
                        .totalProfit(acc.totalProfit)
                        .marginPercent(acc.sellPrice.compareTo(BigDecimal.ZERO) > 0
                                ? acc.sellPrice.subtract(acc.buyPrice).multiply(BigDecimal.valueOf(100))
                                        .divide(acc.sellPrice, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());

        return ProfitAnalysis.builder()
                .totalCostOfGoodsSold(totalCOGS)
                .totalRevenueFromParts(totalRevenue)
                .grossProfit(grossProfit)
                .grossMarginPercent(grossMargin)
                .topProfitableParts(topParts)
                .build();
    }

    // Helper class for accumulating part profits
    private static class PartProfitAccumulator {
        String partName;
        BigDecimal buyPrice;
        BigDecimal sellPrice;
        int totalQty = 0;
        BigDecimal totalProfit = BigDecimal.ZERO;

        PartProfitAccumulator(String partName, BigDecimal buyPrice, BigDecimal sellPrice) {
            this.partName = partName;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }

        void addSale(int qty, BigDecimal profit) {
            this.totalQty += qty;
            this.totalProfit = this.totalProfit.add(profit);
        }
    }

    // ═══════════════════════════════════════════════════
    //  4. QUOTA STATUS
    // ═══════════════════════════════════════════════════

    @Data
    @Builder
    public static class QuotaStatus {
        private String planName;
        private List<QuotaItemDTO> quotas;
    }

    @Data
    @Builder
    public static class QuotaItemDTO {
        private String featureKey;
        private String featureLabel;
        private long currentUsage;
        private long limit; // -1 = unlimited
        private double usagePercent;
    }

    public QuotaStatus getQuotaStatus(Long companyId) {
        // Define hardcoded plan limits (these match the SQL seed data logic)
        // In production, these would come from the Plan entity
        Map<String, Long> planLimits = Map.of(
                "TICKETS", 100L,
                "TECHNICIANS", 10L,
                "INVENTORY", 500L,
                "CUSTOMERS", 1000L
        );

        // Get usage for current month
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        List<UsageTracking> usages = usageTrackingRepository.findAll().stream()
                .filter(u -> u.getCompanyId().equals(companyId))
                .filter(u -> u.getPeriodStart() != null && !u.getPeriodStart().isBefore(monthStart))
                .collect(Collectors.toList());

        Map<String, Integer> usageMap = usages.stream()
                .collect(Collectors.toMap(
                        UsageTracking::getUsageType,
                        u -> u.getCurrentCount() != null ? u.getCurrentCount() : 0,
                        (a, b) -> a));

        List<QuotaItemDTO> quotas = planLimits.entrySet().stream()
                .map(entry -> {
                    long current = usageMap.getOrDefault(entry.getKey(), 0);
                    long limit = entry.getValue();
                    double pct = limit > 0 ? (double) current / limit * 100.0 : 0;

                    return QuotaItemDTO.builder()
                            .featureKey(entry.getKey())
                            .featureLabel(getFeatureLabel(entry.getKey()))
                            .currentUsage(current)
                            .limit(limit)
                            .usagePercent(Math.min(pct, 100.0))
                            .build();
                })
                .collect(Collectors.toList());

        return QuotaStatus.builder()
                .planName("PLAN")
                .quotas(quotas)
                .build();
    }

    private String getFeatureLabel(String key) {
        return switch (key) {
            case "MAX_TICKETS" -> "Servis Fişi";
            case "MAX_TECHNICIANS" -> "Teknisyen";
            case "MAX_INVENTORY" -> "Stok Kalemi";
            case "MAX_CUSTOMERS" -> "Müşteri";
            default -> key;
        };
    }

    // ═══════════════════════════════════════════════════
    //  5. FIELD RADAR
    // ═══════════════════════════════════════════════════

    @Data
    @Builder
    public static class FieldPin {
        private Long technicianId;
        private String technicianName;
        private String coordinates; // "lat,lon"
        private String customerName;
        private String ticketStatus;
        private Long ticketId;
    }

    public List<FieldPin> getFieldRadarPins(Long companyId) {
        List<ServiceTicket> activeTickets = ticketRepository.findByCompanyId(companyId).stream()
                .filter(t -> t.getStatus() != ServiceTicket.TicketStatus.CANCELLED)
                .filter(t -> t.getAssignedTechnicianId() != null)
                .filter(t -> t.getCustomerId() != null)
                .collect(Collectors.toList());

        // Group by technician — show latest ticket location
        Map<Long, ServiceTicket> latestByTech = new LinkedHashMap<>();
        for (ServiceTicket ticket : activeTickets) {
            latestByTech.merge(ticket.getAssignedTechnicianId(), ticket,
                    (existing, incoming) -> {
                        if (incoming.getUpdatedAt() != null && existing.getUpdatedAt() != null
                                && incoming.getUpdatedAt().isAfter(existing.getUpdatedAt())) {
                            return incoming;
                        }
                        return existing;
                    });
        }

        List<FieldPin> pins = new ArrayList<>();
        for (var entry : latestByTech.entrySet()) {
            ServiceTicket ticket = entry.getValue();
            Optional<Customer> customer = customerRepository.findById(ticket.getCustomerId());
            Optional<User> technician = userRepository.findById(entry.getKey());

            if (customer.isPresent() && customer.get().getCoordinates() != null) {
                pins.add(FieldPin.builder()
                        .technicianId(entry.getKey())
                        .technicianName(technician.map(User::getFullName).orElse("Bilinmeyen"))
                        .coordinates(customer.get().getCoordinates())
                        .customerName(customer.get().getName())
                        .ticketStatus(ticket.getStatus().name())
                        .ticketId(ticket.getId())
                        .build());
            }
        }

        return pins;
    }

    // ═══════════════════════════════════════════════════
    //  6. BULK PRICE UPDATE
    // ═══════════════════════════════════════════════════

    @Data
    public static class PriceUpdateDTO {
        private Long inventoryId;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
    }

    public int bulkUpdatePrices(Long companyId, List<PriceUpdateDTO> updates) {
        int count = 0;
        for (PriceUpdateDTO update : updates) {
            Optional<Inventory> opt = inventoryRepository.findById(update.getInventoryId());
            if (opt.isPresent() && opt.get().getCompanyId().equals(companyId)) {
                Inventory inv = opt.get();
                if (update.getBuyPrice() != null) inv.setBuyPrice(update.getBuyPrice());
                if (update.getSellPrice() != null) inv.setSellPrice(update.getSellPrice());
                inventoryRepository.save(inv);
                count++;
            }
        }
        return count;
    }
}
