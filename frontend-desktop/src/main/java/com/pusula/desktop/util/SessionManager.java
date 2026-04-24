package com.pusula.desktop.util;

public class SessionManager {
    private static String authToken;
    private static String username;
    private static String userRole;
    private static Long companyId;

    public static void setSession(String token, String user, String role, Long cId) {
        authToken = token;
        username = user;
        userRole = role;
        companyId = cId;
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

    public static void clearSession() {
        authToken = null;
        username = null;
        userRole = null;
        companyId = null;
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
