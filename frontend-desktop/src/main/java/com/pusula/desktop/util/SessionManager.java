package com.pusula.desktop.util;

public class SessionManager {
    private static String authToken;
    private static String username;

    public static void setSession(String token, String user) {
        authToken = token;
        username = user;
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

    public static void clearSession() {
        authToken = null;
        username = null;
    }

    public static boolean isLoggedIn() {
        return authToken != null;
    }
}
