package com.booking.util;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;

public final class InputValidator {
    private InputValidator() { }

    public static boolean isValidUsername(String username) {
        return username != null && username.matches("^[A-Za-z0-9_.-]{3,30}$");
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 4; // keep simple rule for now
    }

    public static boolean isValidDate(String dateStr) {
        if (dateStr == null) return false;
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static boolean isNotPastDate(String dateStr) {
        if (!isValidDate(dateStr)) return false;
        LocalDate d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        return !d.isBefore(LocalDate.now());
    }
}