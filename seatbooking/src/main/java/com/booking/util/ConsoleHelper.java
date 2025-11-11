package com.booking.util;

import java.util.InputMismatchException;
import java.util.Scanner;

public final class ConsoleHelper {
    private ConsoleHelper() { }

    public static void printHeader(String title) {
        System.out.println();
        System.out.println("--- " + title + " ---");
    }

    public static String prompt(Scanner scanner, String message) {
        System.out.print(message);
        return scanner.nextLine();
    }

    public static int promptInt(Scanner scanner, String message, int min, int max) {
        while (true) {
            try {
                System.out.print(message);
                String line = scanner.nextLine();
                int v = Integer.parseInt(line.trim());
                if (v < min || v > max) {
                    System.out.println("Please enter a number between " + min + " and " + max + ".");
                    continue;
                }
                return v;
            } catch (NumberFormatException | InputMismatchException ex) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }
}