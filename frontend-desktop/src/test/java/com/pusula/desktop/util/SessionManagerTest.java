package com.pusula.desktop.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @AfterEach
    void cleanUp() {
        SessionManager.clearSession();
    }

    @Test
    void expiredSessionIsInvalidatedAndReportedOnlyOnce() {
        AtomicInteger callbacks = new AtomicInteger();
        SessionManager.setSession("token", "admin", "COMPANY_ADMIN", 10L, "PATRON", Map.of());
        SessionManager.setSessionExpiredHandler(callbacks::incrementAndGet);

        Runnable first = SessionManager.expireSession("token");
        Runnable second = SessionManager.expireSession("token");

        assertNotNull(first);
        assertNull(second);
        assertFalse(SessionManager.isLoggedIn());
        first.run();
        assertEquals(1, callbacks.get());
    }

    @Test
    void newLoginResetsExpirationGuard() {
        SessionManager.setSession("first", "admin", "COMPANY_ADMIN", 10L);
        SessionManager.setSessionExpiredHandler(() -> { });
        assertNotNull(SessionManager.expireSession("first"));

        SessionManager.setSession("second", "admin", "COMPANY_ADMIN", 10L);
        SessionManager.setSessionExpiredHandler(() -> { });

        assertNotNull(SessionManager.expireSession("second"));
    }

    @Test
    void delayedFailureFromOldTokenCannotInvalidateNewLogin() {
        SessionManager.setSession("old", "admin", "COMPANY_ADMIN", 10L);
        SessionManager.setSession("new", "admin", "COMPANY_ADMIN", 10L);
        SessionManager.setSessionExpiredHandler(() -> { });

        assertNull(SessionManager.expireSession("old"));
        assertTrue(SessionManager.isLoggedIn());
        assertEquals("new", SessionManager.getToken());
    }
}
