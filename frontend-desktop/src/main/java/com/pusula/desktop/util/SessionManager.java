package com.pusula.desktop.util;

import java.util.Collections;
import java.util.Map;

public class SessionManager {
    private static String authToken;
    private static String username;
    private static String userRole;
    private static Long companyId;
    private static String planType;
    private static Map<String, Boolean> features = Collections.emptyMap();

    public static void setSession(String token, String user, String role, Long cId) {
        setSession(token, user, role, cId, null, Collections.emptyMap());
    }

    public static void setSession(String token, String user, String role, Long cId,
            String plan, Map<String, Boolean> planFeatures) {
        authToken = token;
        username = user;
        userRole = role;
        companyId = cId;
        planType = plan;
        features = planFeatures != null ? Map.copyOf(planFeatures) : Collections.emptyMap();
    }

    public static Long getCompanyId() {
        return companyId;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static String getToken() {
        return authToken;
    }

    public static String getUsername() {
        return username;
    }

    public static String getUserRole() {
        return userRole;
    }

    public static String getPlanType() {
        return planType;
    }

    public static boolean hasFeature(String featureKey) {
        return Boolean.TRUE.equals(features.get(featureKey));
    }

    public static void clearSession() {
        authToken = null;
        username = null;
        userRole = null;
        companyId = null;
        planType = null;
        features = Collections.emptyMap();
    }

    public static boolean isLoggedIn() {
        return authToken != null;
    }

    public static boolean isAdmin() {
        return "COMPANY_ADMIN".equals(userRole) ||
                "SUPER_ADMIN".equals(userRole);
    }

    public static boolean isTechnician() {
        return "TECHNICIAN".equals(userRole);
    }
}
