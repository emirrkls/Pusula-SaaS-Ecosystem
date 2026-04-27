package com.pusula.backend;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
@ComponentScan(basePackages = "com.pusula.backend")
@EnableJpaRepositories(basePackages = "com.pusula.backend.repository")
@EntityScan(basePackages = "com.pusula.backend.entity")
@org.springframework.scheduling.annotation.EnableScheduling
public class PusulaBackendApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PusulaBackendApplication.class);

    private final ApplicationContext applicationContext;
    private final Environment environment;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public PusulaBackendApplication(UserRepository userRepository, CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder, ApplicationContext applicationContext, Environment environment) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(PusulaBackendApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Verify REST Controllers are registered
        String[] controllerBeans = applicationContext.getBeanNamesForAnnotation(RestController.class);
        log.info("Found {} @RestController beans", controllerBeans.length);
        for (String beanName : controllerBeans) {
            Object bean = applicationContext.getBean(beanName);
            log.debug("  ✓ {}", bean.getClass().getName());
        }

        log.info("Users in DB: {}", userRepository.count());

        if (userRepository.count() == 0) {
            log.info("No users found. Seeding default data...");

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

            log.info("Created default admin user: admin / password");
        } else if (isDevProfileActive()) {
            // Keep default admin reset behavior only for local development profile.
            userRepository.findByUsername("admin").ifPresent(admin -> {
                String newHash = passwordEncoder.encode("password");
                admin.setPasswordHash(newHash);
                userRepository.save(admin);
                log.debug("Admin password reset to default");
            });
        }

        userRepository.findAll()
                .forEach(u -> log.debug("User: {} | Role: {}", u.getUsername(), u.getRole()));
    }

    private boolean isDevProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
