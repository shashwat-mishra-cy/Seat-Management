package com.booking.util;

import io.github.cdimascio.dotenv.Dotenv;


public final class AppConfig {
    private static final Dotenv DOTENV = initDotenv();

    private AppConfig() {}

    private static Dotenv initDotenv() {
        try {
            return Dotenv.load();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getenv(String name, String fallback) {
        if (DOTENV != null) {
            try {
                String v = DOTENV.get(name);
                if (v != null && !v.isBlank()) return v;
            } catch (Exception ignored) {
            }
        }
        String v = System.getenv(name);
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    public static String getDbUrl() {
        return getenv("DB_URL", "jdbc:mysql://localhost:3306/seat_management?useSSL=false&allowPublicKeyRetrieval=true");
    }

    public static String getDbUser() {
        return getenv("DB_USER", "root");
    }

    public static String getDbPassword() {
        return getenv("DB_PASSWORD", "");
    }

    public static String getAppName() {
        return getenv("APP_NAME", "SeatManagement");
    }
}