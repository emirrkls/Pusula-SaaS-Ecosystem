package com.pusula.backend.service;

import com.pusula.backend.entity.UsageTracking;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.Plan;
import com.pusula.backend.entity.PlanFeature;
import com.pusula.backend.exception.QuotaExceededException;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.PlanFeatureRepository;
import com.pusula.backend.repository.PlanRepository;
import com.pusula.backend.repository.UsageTrackingRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.ProposalRepository;
import com.pusula.backend.repository.VehicleRepository;
import com.pusula.backend.repository.CommercialDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureServiceUsageConsistencyTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanFeatureRepository planFeatureRepository;
    @Mock
    private UsageTrackingRepository usageTrackingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock private ServiceTicketRepository ticketRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private CommercialDeviceRepository commercialDeviceRepository;
    @Mock private StorageUsageService storageUsageService;

    private FeatureService featureService;
    private final Map<String, UsageTracking> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        featureService = new FeatureService(
                companyRepository,
                planRepository,
                planFeatureRepository,
                usageTrackingRepository,
                userRepository,
                customerRepository,
                inventoryRepository,
                ticketRepository,
                proposalRepository,
                vehicleRepository,
                commercialDeviceRepository,
                storageUsageService);

        lenient().when(usageTrackingRepository.findByCompanyIdAndUsageTypeAndPeriodStart(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Long companyId = invocation.getArgument(0);
                    String usageType = invocation.getArgument(1);
                    LocalDate periodStart = invocation.getArgument(2);
                    return Optional.ofNullable(store.get(key(companyId, usageType, periodStart)));
                });

        lenient().when(usageTrackingRepository.save(any(UsageTracking.class)))
                .thenAnswer(invocation -> {
                    UsageTracking tracking = invocation.getArgument(0);
                    store.put(key(tracking.getCompanyId(), tracking.getUsageType(), tracking.getPeriodStart()), tracking);
                    return tracking;
                });
    }

    @Test
    void incrementUsage_keepsCompanyMonthlyCountsIndependent() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

        featureService.incrementUsage(10L, "TICKETS");
        featureService.incrementUsage(10L, "TICKETS");
        featureService.incrementUsage(20L, "TICKETS");
        featureService.incrementUsage(10L, "PROPOSALS");

        assertEquals(2, store.get(key(10L, "TICKETS", currentMonth)).getCurrentCount());
        assertEquals(1, store.get(key(20L, "TICKETS", currentMonth)).getCurrentCount());
        assertEquals(1, store.get(key(10L, "PROPOSALS", currentMonth)).getCurrentCount());
    }

    @Test
    void incrementUsage_rollsOverToNewMonthRecord() {
        LocalDate previousMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

        UsageTracking oldMonth = new UsageTracking();
        oldMonth.setCompanyId(10L);
        oldMonth.setUsageType("TICKETS");
        oldMonth.setPeriodStart(previousMonth);
        oldMonth.setPeriodEnd(previousMonth.plusMonths(1).minusDays(1));
        oldMonth.setCurrentCount(7);
        store.put(key(10L, "TICKETS", previousMonth), oldMonth);

        featureService.incrementUsage(10L, "TICKETS");

        assertEquals(7, store.get(key(10L, "TICKETS", previousMonth)).getCurrentCount());
        assertEquals(1, store.get(key(10L, "TICKETS", currentMonth)).getCurrentCount());

        ArgumentCaptor<LocalDate> periodCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(usageTrackingRepository, atLeastOnce())
                .findByCompanyIdAndUsageTypeAndPeriodStart(eq(10L), eq("TICKETS"), periodCaptor.capture());
        assertEquals(currentMonth, periodCaptor.getValue());
    }

    @Test
    void checkQuota_whenLimitReached_throwsQuotaExceeded() {
        Company company = new Company();
        company.setId(10L);
        company.setPlanType(PlanType.CIRAK);

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        UsageTracking tracking = new UsageTracking();
        tracking.setCompanyId(10L);
        tracking.setUsageType("TICKETS");
        tracking.setPeriodStart(currentMonth);
        tracking.setPeriodEnd(currentMonth.plusMonths(1).minusDays(1));
        tracking.setCurrentCount(30);
        store.put(key(10L, "TICKETS", currentMonth), tracking);

        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        Plan plan = new Plan();
        plan.setName("CIRAK");
        plan.setMaxMonthlyTickets(30);
        when(planRepository.findByName("CIRAK")).thenReturn(Optional.of(plan));
        when(ticketRepository.countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(10L), any(), any())).thenReturn(30L);

        assertThrows(QuotaExceededException.class, () -> featureService.checkQuota(10L, "TICKETS"));
    }

    @Test
    void customerQuota_usesPlanRecordAndCurrentActiveRows() {
        Company company = new Company();
        company.setId(10L);
        company.setPlanType(PlanType.CIRAK);
        Plan plan = new Plan();
        plan.setName("CIRAK");
        plan.setMaxCustomers(100);

        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(planRepository.findByName("CIRAK")).thenReturn(Optional.of(plan));
        when(customerRepository.countByCompanyId(10L)).thenReturn(100L);

        assertThrows(QuotaExceededException.class, () -> featureService.checkQuota(10L, "CUSTOMERS"));
    }

    @Test
    void featureFlags_areReadFromPlanFeatureRows() {
        Plan plan = new Plan();
        plan.setId(3L);
        plan.setName("PATRON");
        PlanFeature feature = new PlanFeature();
        feature.setPlan(plan);
        feature.setFeatureKey("COMMERCIAL_DEVICES");
        feature.setEnabled(true);

        when(planRepository.findByName("PATRON")).thenReturn(Optional.of(plan));
        when(planFeatureRepository.findByPlanId(3L)).thenReturn(java.util.List.of(feature));

        assertEquals(true, featureService.getFeatureFlags(PlanType.PATRON).get("COMMERCIAL_DEVICES"));
    }

    private String key(Long companyId, String usageType, LocalDate periodStart) {
        return companyId + ":" + usageType + ":" + periodStart;
    }
}
