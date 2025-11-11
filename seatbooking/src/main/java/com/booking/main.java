package com.booking;

import com.booking.service.AuthProvider;
import com.booking.service.AuthService;
import com.booking.service.TrainService;
import com.booking.service.BookingService;
import com.booking.service.Database;
import com.booking.exception.AuthException;
import com.booking.exception.DatabaseException;
import com.booking.util.AppUI;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Database db;
        try {
            db = new Database();
            AuthProvider authService = new AuthService(db);
            TrainService trainService = new TrainService(db);
            BookingService bookingService = new BookingService(trainService, db);

            Scanner scanner = new Scanner(System.in);
            AppUI ui = new AppUI(authService, trainService, bookingService, scanner);
            ui.run();
        } catch (AuthException | DatabaseException e) {
            System.err.println("Fatal: failed to initialize application: " + e.getMessage());
        }
    }
}