package com.pusula.backend.config;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final InventoryRepository inventoryRepository;
    private final ServiceTicketRepository serviceTicketRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
            CustomerRepository customerRepository,
            InventoryRepository inventoryRepository,
            ServiceTicketRepository serviceTicketRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.inventoryRepository = inventoryRepository;
        this.serviceTicketRepository = serviceTicketRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Ensure a company exists
        Company company = companyRepository.findAll().stream().findFirst().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName("Pusula Tech");
            newCompany.setSubscriptionStatus("ACTIVE");
            return companyRepository.save(newCompany);
        });

        // 1. Users (Admin is already created by PusulaBackendApplication)
        if (userRepository.findByUsername("tech1").isEmpty()) {
            User tech1 = new User();
            tech1.setUsername("tech1");
            tech1.setPasswordHash(passwordEncoder.encode("password"));
            tech1.setRole("TECHNICIAN");
            tech1.setFullName("Ali Veli");
            tech1.setCompanyId(company.getId());
            userRepository.save(tech1);
        }

        // 2. Customers
        if (customerRepository.count() == 0) {
            Customer c1 = new Customer();
            c1.setName("Ahmet Yilmaz");
            c1.setPhone("555-111-2233");
            c1.setAddress("Istanbul, Turkey");
            c1.setCompanyId(company.getId());
            customerRepository.save(c1);

            Customer c2 = new Customer();
            c2.setName("Ayse Demir");
            c2.setPhone("555-444-5566");
            c2.setAddress("Ankara, Turkey");
            c2.setCompanyId(company.getId());
            customerRepository.save(c2);

            Customer c3 = new Customer();
            c3.setName("Mehmet Kaya");
            c3.setPhone("555-777-8899");
            c3.setAddress("Izmir, Turkey");
            c3.setCompanyId(company.getId());
            customerRepository.save(c3);
        }

        // 3. Inventory
        if (inventoryRepository.count() == 0) {
            Inventory i1 = new Inventory();
            i1.setPartName("iPhone 13 Screen");
            i1.setQuantity(15);
            i1.setBuyPrice(new BigDecimal("50.00"));
            i1.setSellPrice(new BigDecimal("120.00"));
            i1.setCriticalLevel(5);
            i1.setCompanyId(company.getId());
            inventoryRepository.save(i1);

            Inventory i2 = new Inventory();
            i2.setPartName("Samsung S21 Battery");
            i2.setQuantity(3); // Critical!
            i2.setBuyPrice(new BigDecimal("20.00"));
            i2.setSellPrice(new BigDecimal("45.00"));
            i2.setCriticalLevel(5);
            i2.setCompanyId(company.getId());
            inventoryRepository.save(i2);

            Inventory i3 = new Inventory();
            i3.setPartName("USB-C Charging Port");
            i3.setQuantity(50);
            i3.setBuyPrice(new BigDecimal("5.00"));
            i3.setSellPrice(new BigDecimal("25.00"));
            i3.setCriticalLevel(10);
            i3.setCompanyId(company.getId());
            inventoryRepository.save(i3);
        }

        // 4. Service Tickets
        if (serviceTicketRepository.count() == 0) {
            User tech1 = userRepository.findByUsername("tech1").orElse(null);
            Customer c1 = customerRepository.findAll().get(0);
            Customer c2 = customerRepository.findAll().get(1);
            Customer c3 = customerRepository.findAll().get(2);

            ServiceTicket t1 = new ServiceTicket();
            t1.setCustomerId(c1.getId());
            t1.setDescription("Device: iPhone 13 - Issue: Broken Screen");
            t1.setStatus(ServiceTicket.TicketStatus.PENDING);
            t1.setScheduledDate(LocalDateTime.now().withHour(10).withMinute(0));
            if (tech1 != null)
                t1.setAssignedTechnicianId(tech1.getId());
            t1.setCompanyId(company.getId());
            serviceTicketRepository.save(t1);

            ServiceTicket t2 = new ServiceTicket();
            t2.setCustomerId(c2.getId());
            t2.setDescription("Device: Samsung S21 - Issue: Battery draining fast");
            t2.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
            t2.setScheduledDate(LocalDateTime.now().withHour(14).withMinute(30));
            if (tech1 != null)
                t2.setAssignedTechnicianId(tech1.getId());
            t2.setCompanyId(company.getId());
            serviceTicketRepository.save(t2);

            ServiceTicket t3 = new ServiceTicket();
            t3.setCustomerId(c3.getId());
            t3.setDescription("Device: MacBook Pro - Issue: Keyboard not working");
            t3.setStatus(ServiceTicket.TicketStatus.PENDING);
            t3.setScheduledDate(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
            t3.setCompanyId(company.getId());
            serviceTicketRepository.save(t3);
        }

        System.out.println("Sample data seeded successfully via DataSeeder!");
    }
}
