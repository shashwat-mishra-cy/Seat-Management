package com.booking.util;

import com.booking.model.User;
import com.booking.model.Train;
import com.booking.model.Seat;
import com.booking.model.Ticket;
import com.booking.model.Role;
import com.booking.service.AuthProvider;
import com.booking.service.TrainService;
import com.booking.service.BookingService;
import com.booking.exception.ValidationException;
import com.booking.util.InputValidator;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class AppUI {

    private final AuthProvider authService;
    private final TrainService trainService;
    private final BookingService bookingService;
    private final Scanner scanner;

    public AppUI(AuthProvider authService, TrainService trainService, BookingService bookingService, Scanner scanner) {
        this.authService = authService;
        this.trainService = trainService;
        this.bookingService = bookingService;
        this.scanner = scanner;
    }

    public void run() {
        User loggedInUser = null;

        while (true) {
            ConsoleHelper.printHeader("Welcome to the Train Booking System");
            System.out.println("1. Login");
            System.out.println("2. Register New User");
            System.out.println("3. Exit");

            int choice = ConsoleHelper.promptInt(scanner, "Please choose an option: ", 1, 3);

            switch (choice) {
                case 1:
                    String username = ConsoleHelper.prompt(scanner, "Enter username: ");
                    String password = ConsoleHelper.prompt(scanner, "Enter password: ");
                    loggedInUser = authService.login(username, password);

                    if (loggedInUser != null) {
                        if (loggedInUser.getRole() == Role.ADMIN) {
                            showAdminMenu();
                        } else {
                            showPassengerMenu(loggedInUser);
                        }
                        authService.recordLogout(loggedInUser.getUsername());
                        loggedInUser = null;
                    }
                    break;
                case 2:
                    String newUsername = ConsoleHelper.prompt(scanner, "Enter desired username: ");
                    String newPassword = ConsoleHelper.prompt(scanner, "Enter desired password: ");
                    authService.register(newUsername, newPassword);
                    break;
                case 3:
                    System.out.println("Thank you for using the system. Goodbye!");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid choice. Please enter 1, 2, or 3.");
            }
        }
    }

    private void showAdminMenu() {
        while (true) {
            System.out.println("\n--- ADMIN MENU ---");
            System.out.println("1. Add New Train");
            System.out.println("2. View All Trains");
            System.out.println("3. Check Passenger Details (View All Bookings)");
            System.out.println("4. Logout");

            int choice = ConsoleHelper.promptInt(scanner, "Please choose an option: ", 1, 4);

            switch (choice) {
                case 1:
                    handleAddTrain();
                    break;
                case 2:
                    handleViewAllTrains();
                    break;
                case 3:
                    handleViewAllBookings();
                    break;
                case 4:
                    System.out.println("Logging out admin...");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    private void handleAddTrain() {
        ConsoleHelper.printHeader("Add New Train");
        String trainNumber = ConsoleHelper.prompt(scanner, "Enter Train Number (e.g., T123): ");
        String trainName = ConsoleHelper.prompt(scanner, "Enter Train Name (e.g., City Express): ");
        String routeStr = ConsoleHelper.prompt(scanner, "Enter Route (comma-separated, e.g., Mumbai,Pune,Delhi): ");
        List<String> route = Arrays.stream(routeStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (route.size() < 2) {
            System.out.println("Error: A route must contain at least two stops (start and end).");
            return;
        }
        int totalSeats = ConsoleHelper.promptInt(scanner, "Enter Total Seats (e.g., 50): ", 1, 10000);

        trainService.addTrain(trainNumber, trainName, route, totalSeats);
    }

    private void handleViewAllTrains() {
        System.out.println("\n--- All Trains in System ---");
        List<Train> trains = trainService.getAllTrains();
        if (trains.isEmpty()) {
            System.out.println("No trains have been added to the system yet.");
            return;
        }
        for (Train train : trains) {
            System.out.println("--------------------");
            System.out.println("Train Name: " + train.getTrainName());
            System.out.println("Train Number: " + train.getTrainNumber());
            System.out.println("Route: " + String.join(" -> ", train.getRoute()));
            System.out.println("Total Seats: " + train.getSeats().size());
            System.out.println("Booked Seats: " + train.getBookedSeatCount());
            System.out.println("Available Seats: " + train.getAvailableSeatCount());
            List<String> booked = train.getSeats().stream().filter(Seat::isBooked).map(Seat::getSeatNumber).toList();
            if (!booked.isEmpty()) {
                System.out.println("Booked Seat Numbers: " + String.join(", ", booked));
            }
        }
    }

    private void handleViewAllBookings() {
        System.out.println("\n--- All Passenger Bookings ---");
        List<Ticket> allTickets = bookingService.getAllTickets();
        if (allTickets.isEmpty()) {
            System.out.println("No tickets have been booked in the system yet.");
        } else {
            System.out.println("Total bookings: " + allTickets.size());
            for (Ticket ticket : allTickets) {
                ticket.displayTicketDetails();
            }
        }
    }

    private void showPassengerMenu(User passenger) {
        while (true) {
            System.out.println("1. Book New Ticket");
            System.out.println("2. View My Bookings");
            System.out.println("3. Cancel Ticket");
            System.out.println("4. Logout");

            int choice = ConsoleHelper.promptInt(scanner, "Please choose an option: ", 1, 4);

            switch (choice) {
                case 1:
                    handleBookTicket(passenger);
                    break;
                case 2:
                    handleViewBookings(passenger);
                    break;
                case 3:
                    handleCancelTicket(passenger);
                    break;
                case 4:
                    System.out.println("Logging out " + passenger.getUsername() + "...");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    private void handleBookTicket(User passenger) {
        String date = ConsoleHelper.prompt(scanner, "Enter Date (YYYY-MM-DD): ");
        if (!InputValidator.isValidDate(date)) {
            System.out.println("Invalid date format. Please use YYYY-MM-DD.");
            return;
        }
        if (!InputValidator.isNotPastDate(date)) {
            System.out.println("Travel date cannot be before today.");
            return;
        }
        String startStation = ConsoleHelper.prompt(scanner, "Enter Start Station (e.g., Mumbai, Delhi): ");
        String endStation = ConsoleHelper.prompt(scanner, "Enter End Station (e.g., Pune, Jaipur): ");

        List<Train> availableTrains = trainService.searchTrains(startStation, endStation);
        if (availableTrains.isEmpty()) {
            System.out.println("No trains found for your route. Returning to menu.");
            return;
        }

        System.out.println("Available Trains:");
        for (int i = 0; i < availableTrains.size(); i++) {
            Train train = availableTrains.get(i);
            System.out.println((i + 1) + ". " + train.getTrainName() + " (" + train.getTrainNumber() + ")");
        }
        int trainChoice = ConsoleHelper.promptInt(scanner, "Select a train (enter number): ", 1,
                availableTrains.size());
        Train selectedTrain = availableTrains.get(trainChoice - 1);

        int available = selectedTrain.getAvailableSeatCount();
        System.out.println("Available seats: " + available);
        if (available <= 0) {
            System.out.println("No seats available on this train. Returning to menu.");
            return;
        }

        int seatsToBook = ConsoleHelper.promptInt(scanner, "How many seats would you like to book? ", 1, available);
        String confirmation = ConsoleHelper.prompt(scanner,
                "Confirm booking of " + seatsToBook + " seat(s) on " + selectedTrain.getTrainName() + "? (yes/no): ");
        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Booking cancelled.");
            return;
        }

        try {
            java.util.List<String> usernames = new java.util.ArrayList<>();
            for (int i = 1; i <= seatsToBook; i++) {
                String u = ConsoleHelper
                        .prompt(scanner,
                                "Enter passenger username for ticket " + i + " (leave empty to book for yourself): ")
                        .trim();
                if (u.isEmpty())
                    u = passenger.getUsername();
                usernames.add(u);
            }

            List<Ticket> newTickets = bookingService.createTicketsForUsernames(usernames, selectedTrain, date,
                    passenger.getUsername());
            if (newTickets == null || newTickets.isEmpty()) {
                System.out.println("Booking failed. No tickets were created.");
            } else {
                System.out.println("\nBooking successful! Created " + newTickets.size() + " ticket(s):");
                for (Ticket t : newTickets)
                    t.displayTicketDetails();
            }
        } catch (ValidationException ve) {
            System.out.println("Booking failed: " + ve.getMessage());
        }
    }

    private void handleViewBookings(User passenger) {
        ConsoleHelper.printHeader("My Bookings");

    List<Ticket> upcoming = bookingService.findTicketsByPassenger(passenger);
    List<Ticket> cancelled = bookingService.findCancelledTicketsByPassenger(passenger);
    List<Ticket> past = bookingService.findPastTicketsByPassenger(passenger);

        System.out.println("\nUpcoming / Active Journeys:");
        if (upcoming == null || upcoming.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (Ticket ticket : upcoming) {
                ticket.displayTicketDetails();
            }
        }

        System.out.println("\nPast Journeys:");
        if (past == null || past.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (Ticket ticket : past) {
                ticket.displayTicketDetails();
            }
        }
        
        System.out.println("\nCancelled Journeys:");
        if (cancelled == null || cancelled.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (Ticket ticket : cancelled) {
                ticket.displayTicketDetails();
            }
        }
    }

    private void handleCancelTicket(User passenger) {
        System.out.println("\n--- Cancel Ticket ---");
        List<Ticket> myTickets = bookingService.findTicketsByPassenger(passenger);
        if (myTickets.isEmpty()) {
            System.out.println("You have no active bookings to cancel.");
            return;
        }

        System.out.println("Your active bookings:");
        for (int i = 0; i < myTickets.size(); i++) {
            Ticket t = myTickets.get(i);
            System.out.println((i + 1) + ") PNR: " + t.getPnrNumber() + " | Train: " + t.getTrain().getTrainName()
                    + " (" + t.getTrain().getTrainNumber() + ") | Seat: " + t.getSeat().getSeatNumber() + " | Date: "
                    + t.getTravelDate());
        }

        String sel = ConsoleHelper.prompt(scanner, "Select a ticket to cancel (enter number) or 'q' to go back: ")
                .trim();
        if (sel.equalsIgnoreCase("q") || sel.isEmpty()) {
            System.out.println("Cancellation aborted.");
            return;
        }

        int idx;
        try {
            idx = Integer.parseInt(sel);
        } catch (NumberFormatException e) {
            System.out.println("Invalid selection.");
            return;
        }

        if (idx < 1 || idx > myTickets.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        Ticket ticket = myTickets.get(idx - 1);
        System.out.println("Found ticket:");
        ticket.displayTicketDetails();
        String confirmation = ConsoleHelper.prompt(scanner, "Are you sure you want to cancel this ticket? (yes/no): ");

        if (confirmation.equalsIgnoreCase("yes")) {
            if (bookingService.cancelTicket(ticket)) {
                System.out.println("Ticket " + ticket.getPnrNumber() + " has been successfully cancelled.");
                System.out.println("Seat " + ticket.getSeat().getSeatNumber() + " is now available.");
            } else {
                System.out.println("Error: Could not cancel ticket.");
            }
        } else {
            System.out.println("Cancellation aborted.");
        }
    }
}