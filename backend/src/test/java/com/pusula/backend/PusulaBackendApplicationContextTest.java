package com.pusula.backend;

import com.pusula.backend.network.NetworkMembershipRepository;
import com.pusula.backend.network.NetworkOrderRepository;
import com.pusula.backend.network.NetworkPolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Guards the production application scan boundaries, not a test-only repository configuration. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application_context;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "jwt.secret=test-only-secret-that-is-long-enough-for-hmac-signing-1234567890",
        "jwt.expiration=86400000",
        "app.upload.base-dir=target/application-context-uploads",
        "apple.push.enabled=false"
})
class PusulaBackendApplicationContextTest {
    @Autowired NetworkOrderRepository orders;
    @Autowired NetworkMembershipRepository memberships;
    @Autowired NetworkPolicyRepository policies;

    @Test void productionApplicationRegistersServiceNetworkRepositories() {
        assertNotNull(orders);
        assertNotNull(memberships);
        assertNotNull(policies);
    }
}
