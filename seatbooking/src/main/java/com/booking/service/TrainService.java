package com.booking.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.booking.model.Train;
import com.booking.model.Seat;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.booking.exception.DatabaseException;
import com.booking.exception.ValidationException;


public class TrainService {

    private static final Logger LOGGER = Logger.getLogger(TrainService.class.getName());

    private final DatabaseProvider db;
    private final List<Train> trains;

    public TrainService(DatabaseProvider db) {
        this.db = db;
        this.trains = new ArrayList<>();

        try {
            this.db.init();
        } catch (DatabaseException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize DB schema for TrainService", e);
            throw e;
        }

        loadTrainsFromDb();
        if (this.trains.isEmpty()) {
            initializeTrains();
            this.trains.clear();
            loadTrainsFromDb();
        }
    }

    private void initializeTrains() {
        Train t1 = new Train("T123", "City Express", Arrays.asList("Mumbai", "Pune", "Delhi"), 50);
        Train t2 = new Train("T456", "Deccan Queen", Arrays.asList("Mumbai", "Thane", "Pune"), 80);
        Train t3 = new Train("T789", "Capital Mail", Arrays.asList("Delhi", "Jaipur", "Ahmedabad"), 60);

        this.trains.add(t1);
        this.trains.add(t2);
        this.trains.add(t3);
    }

    private void loadTrainsFromDb() {
        String sql = "SELECT train_number, train_name, route, total_seats FROM trains";
        try (Connection c = this.db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String number = rs.getString("train_number");
                String name = rs.getString("train_name");
                String routeCsv = rs.getString("route");
                int totalSeats = rs.getInt("total_seats");

                List<String> route = new ArrayList<>();
                if (routeCsv != null && !routeCsv.isEmpty()) {
                    route = Arrays.stream(routeCsv.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }

                Train t = new Train(number, name, route, totalSeats);
                this.trains.add(t);
            }

            try (PreparedStatement ps2 = c.prepareStatement("SELECT train_number, seat_number FROM tickets WHERE status = 'ACTIVE'"); ResultSet rs2 = ps2.executeQuery()) {
                while (rs2.next()) {
                    String tnum = rs2.getString("train_number");
                    String seatNum = rs2.getString("seat_number");

                    for (Train train : this.trains) {
                        if (train.getTrainNumber().equalsIgnoreCase(tnum)) {
                            for (Seat seat : train.getSeats()) {
                                if (seat.getSeatNumber().equalsIgnoreCase(seatNum)) {
                                    seat.book();
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Error marking booked seats from tickets", ex);
            }

        } catch (DatabaseException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading trains from DB", e);
        }
    }

    public List<Train> searchTrains(String startStation, String endStation) {
        String start = startStation == null ? "" : startStation.trim();
        String end = endStation == null ? "" : endStation.trim();
        List<Train> availableTrains = new ArrayList<>();
        for (Train train : this.trains) {
            if (train.hasStops(start, end)) {
                availableTrains.add(train);
            }
        }
        return availableTrains;
    }

    public void displaySeats(Train train) {
        System.out.println("Available seats for " + train.getTrainName() + ":");
        for (Seat seat : train.getSeats()) {
            if (!seat.isBooked()) {
                System.out.print(seat.getSeatNumber() + " ");
            }
        }
        System.out.println();
    }

    public Seat findSeat(Train train, String seatNumber) {
        for (Seat seat : train.getSeats()) {
            if (seat.getSeatNumber().equalsIgnoreCase(seatNumber) && !seat.isBooked()) {
                return seat;
            }
        }
        return null;
    }

    public boolean addTrain(String trainNumber, String trainName, List<String> route, int totalSeats) {
        List<String> normalizedRoute = (route == null) ? new ArrayList<>() : route.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (normalizedRoute.size() < 2) {
            throw new ValidationException("A train route must contain at least two stops.");
        }
        for (Train train : this.trains) {
            if (train.getTrainNumber().equalsIgnoreCase(trainNumber)) {
                System.out.println("Error: Train Number already exists.");
                return false;
            }
        }
        String sql = "INSERT INTO trains (train_number, train_name, route, total_seats) VALUES (?, ?, ?, ?)";
        String routeCsv = String.join(",", normalizedRoute);
        try (Connection c = this.db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, trainNumber);
            ps.setString(2, trainName);
            ps.setString(3, routeCsv);
            ps.setInt(4, totalSeats);
            ps.executeUpdate();

            Train newTrain = new Train(trainNumber, trainName, route, totalSeats);
            this.trains.add(newTrain);
            System.out.println("Train " + trainName + " added successfully.");
            return true;
        } catch (DatabaseException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding train to DB", e);
            System.out.println("Error adding train to DB: " + e.getMessage());
            return false;
        }
    }

    public List<Train> getAllTrains() {
        return this.trains;
    }
}