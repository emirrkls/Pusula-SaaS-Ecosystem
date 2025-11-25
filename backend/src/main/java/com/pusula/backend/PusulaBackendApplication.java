package com.pusula.backend;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class PusulaBackendApplication implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public PusulaBackendApplication(UserRepository userRepository, CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static void main(String[] args) {
        SpringApplication.run(PusulaBackendApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- STARTUP DATA CHECK ---");
        System.out.println("Number of users in DB: " + userRepository.count());

        if (userRepository.count() == 0) {
            System.out.println("No users found. Seeding default data...");

            Company company = companyRepository.findAll().stream().findFirst().orElseGet(() -> {
                Company newCompany = new Company();
                newCompany.setName("Pusula Tech");
                newCompany.setSubscriptionStatus("ACTIVE");
                return companyRepository.save(newCompany);
            });

            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("password"));
            admin.setRole("SUPER_ADMIN");
            admin.setCompanyId(company.getId());
            admin.setFullName("System Admin");
            userRepository.save(admin);

            System.out.println("CREATED DEFAULT ADMIN USER: admin / password");
        } else {
            // Fix: Reset admin password to ensure it works
            userRepository.findByUsername("admin").ifPresent(admin -> {
                String newHash = passwordEncoder.encode("password");
                admin.setPasswordHash(newHash);
                userRepository.save(admin);
                System.out.println("!!! ADMIN PASSWORD RESET TO 'password' !!!");
            });
        }

        userRepository.findAll()
                .forEach(u -> System.out.println("User: " + u.getUsername() + " | Role: " + u.getRole()));
        System.out.println("--------------------------");
    }
}
