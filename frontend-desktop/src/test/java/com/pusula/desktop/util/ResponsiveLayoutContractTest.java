package com.pusula.desktop.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ResponsiveLayoutContractTest {
    @Test
    void globalStylesDefineCompactAndNarrowBreakpoints() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/css/styles.css")) {
            assertNotNull(input);
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(css.contains(".compact-ui .sidebar"));
            assertTrue(css.contains(".narrow-ui .sidebar"));
            assertTrue(css.contains(".responsive-page-viewport"));
            assertTrue(css.contains(".narrow-ui .page-header-title"));
            assertTrue(css.contains("-fx-min-height: 38"));
        }
    }
}
