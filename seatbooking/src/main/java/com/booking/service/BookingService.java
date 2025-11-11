package com.booking.service;

import com.booking.exception.ValidationException;
import com.booking.model.Ticket;
import com.booking.model.User;
import com.booking.model.Train;
import com.booking.model.Seat;
import com.booking.model.Role;
import com.booking.util.InputValidator;

import java.util.ArrayList;
import java.util.List;
import com.booking.util.PnrGenerator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class BookingService {

    private List<Ticket> allTickets;
    private TrainService trainService;
    private final DatabaseProvider db;

    public BookingService(TrainService trainService, DatabaseProvider db) {
        this.allTickets = new ArrayList<>();
        this.trainService = trainService;
        this.db = db;

        try {
            this.db.init();
        } catch (com.booking.exception.DatabaseException e) {
            throw e;
        }
        loadActiveTicketsFromDb();
    }

    public Ticket createTicket(User passenger, Train train, Seat seat, String date) {
        if (date == null || !InputValidator.isValidDate(date)) {
            throw new ValidationException("Invalid travel date format. Expected YYYY-MM-DD.");
        }
        if (!InputValidator.isNotPastDate(date)) {
            throw new ValidationException("Travel date cannot be before today.");
        }
        String pnr = PnrGenerator.generate();
        String sql = "INSERT INTO tickets(pnr, username, train_number, seat_number, travel_date, booked_by, status) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = this.db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pnr);
            ps.setString(2, passenger.getUsername());
            ps.setString(3, train.getTrainNumber());
            ps.setString(4, seat.getSeatNumber());
            ps.setString(5, date);
            ps.setString(6, passenger.getUsername());
            ps.setString(7, "ACTIVE");
            ps.executeUpdate();

            Ticket newTicket = new Ticket(pnr, passenger, train, seat, date);
            this.allTickets.add(newTicket);
            seat.book();

            String findId = "SELECT id FROM users WHERE username = ?";
            Integer userId = null;
            try (Connection conn2 = this.db.getConnection(); PreparedStatement ps2 = conn2.prepareStatement(findId)) {
                ps2.setString(1, passenger.getUsername());
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) userId = rs2.getInt("id");
                }
            } catch (SQLException | com.booking.exception.DatabaseException e) {
            }

            String insertHistory = "INSERT INTO user_history(user_id, pnr, username, action, details) VALUES(?,?,?,?,?)";
            try (Connection conn3 = this.db.getConnection(); PreparedStatement ps3 = conn3.prepareStatement(insertHistory)) {
                if (userId != null) ps3.setInt(1, userId); else ps3.setNull(1, java.sql.Types.INTEGER);
                ps3.setString(2, pnr);
                ps3.setString(3, passenger.getUsername());
                ps3.setString(4, "BOOK");
                ps3.setString(5, "Booked seat " + seat.getSeatNumber() + " on train " + train.getTrainNumber());
                ps3.executeUpdate();
            } catch (SQLException | com.booking.exception.DatabaseException e) {
                try {
                    String legacy = "INSERT INTO user_history(username, action, details) VALUES(?,?,?)";
                    try (Connection c2 = this.db.getConnection(); PreparedStatement ps2 = c2.prepareStatement(legacy)) {
                        ps2.setString(1, passenger.getUsername());
                        ps2.setString(2, "BOOK");
                        ps2.setString(3, "Booked seat " + seat.getSeatNumber() + " on train " + train.getTrainNumber());
                        ps2.executeUpdate();
                    }
                } catch (SQLException | com.booking.exception.DatabaseException ex) {
                }
            }

            return newTicket;
        } catch (com.booking.exception.DatabaseException | SQLException e) {
            System.out.println("Error creating ticket in DB: " + e.getMessage());
            return null;
        }
    }

    public List<Ticket> createTickets(User passenger, Train train, int numSeats, String date) {
        List<Ticket> created = new ArrayList<>();
        if (numSeats <= 0) throw new ValidationException("Number of seats to book must be at least 1.");

        Connection conn = null;
        try {
            conn = this.db.getConnection();
            conn.setAutoCommit(false);

            String lockSql = "SELECT seat_number FROM tickets WHERE train_number = ? AND status = 'ACTIVE' FOR UPDATE";
            List<String> activeSeatNumbers = new ArrayList<>();
            try (PreparedStatement psLock = conn.prepareStatement(lockSql)) {
                psLock.setString(1, train.getTrainNumber());
                try (ResultSet rs = psLock.executeQuery()) {
                    while (rs.next()) activeSeatNumbers.add(rs.getString("seat_number"));
                }
            }

            List<Seat> availableSeats = new ArrayList<>();
            for (Seat s : train.getSeats()) {
                if (!activeSeatNumbers.contains(s.getSeatNumber())) availableSeats.add(s);
            }

            if (availableSeats.size() < numSeats) {
                conn.rollback();
                throw new ValidationException("Not enough seats available. Requested " + numSeats + ", available " + availableSeats.size());
            }

            Integer userId = null;
            String findId = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement psFind = conn.prepareStatement(findId)) {
                psFind.setString(1, passenger.getUsername());
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) userId = rs.getInt("id");
                }
            } catch (SQLException ignored) {
            }

            String insertTicketSql = "INSERT INTO tickets(pnr, username, train_number, seat_number, travel_date, status) VALUES(?,?,?,?,?,?)";
            String insertHistorySql = "INSERT INTO user_history(user_id, pnr, action, details) VALUES(?,?,?,?)";

            try (PreparedStatement psTicket = conn.prepareStatement(insertTicketSql);
                 PreparedStatement psHistory = conn.prepareStatement(insertHistorySql)) {

                for (int i = 0; i < numSeats; i++) {
                    Seat seatToBook = availableSeats.get(i);
                    String pnr = PnrGenerator.generate();

                    psTicket.setString(1, pnr);
                    psTicket.setString(2, passenger.getUsername());
                    psTicket.setString(3, train.getTrainNumber());
                    psTicket.setString(4, seatToBook.getSeatNumber());
                    psTicket.setString(5, date);
                    psTicket.setString(6, "ACTIVE");
                    psTicket.executeUpdate();

                    try {
                        if (userId != null) psHistory.setInt(1, userId); else psHistory.setNull(1, java.sql.Types.INTEGER);
                        psHistory.setString(2, pnr);
                        psHistory.setString(3, "BOOK");
                        psHistory.setString(4, "Booked seat " + seatToBook.getSeatNumber() + " on train " + train.getTrainNumber());
                        psHistory.executeUpdate();
                    } catch (SQLException he) {
                        try (PreparedStatement psLegacy = conn.prepareStatement("INSERT INTO user_history(username, action, details) VALUES(?,?,?)")) {
                            psLegacy.setString(1, passenger.getUsername());
                            psLegacy.setString(2, "BOOK");
                            psLegacy.setString(3, "Booked seat " + seatToBook.getSeatNumber() + " on train " + train.getTrainNumber());
                            psLegacy.executeUpdate();
                        } catch (SQLException ignored) {
                        }
                    }

                    User u = new User(passenger.getUsername(), "", Role.PASSENGER);
                    Ticket t = new Ticket(pnr, u, train, seatToBook, date);
                    created.add(t);
                }
            }

            conn.commit();

            for (Ticket t : created) {
                for (Seat s : train.getSeats()) {
                    if (s.getSeatNumber().equalsIgnoreCase(t.getSeat().getSeatNumber())) {
                        s.book();
                        break;
                    }
                }
                this.allTickets.add(t);
            }

            return created;
        } catch (SQLException | com.booking.exception.DatabaseException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
            }
            throw new com.booking.exception.DatabaseException("Failed to create tickets transactionally", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<Ticket> createTicketsForUsernames(List<String> usernames, Train train, String date, String bookedBy) {
        if (usernames == null || usernames.isEmpty()) throw new ValidationException("No usernames provided");
        int numSeats = usernames.size();
        List<Ticket> created = new ArrayList<>();

        Connection conn = null;
        try {
            conn = this.db.getConnection();
            conn.setAutoCommit(false);

            String lockSql = "SELECT seat_number FROM tickets WHERE train_number = ? AND status = 'ACTIVE' FOR UPDATE";
            List<String> activeSeatNumbers = new ArrayList<>();
            try (PreparedStatement psLock = conn.prepareStatement(lockSql)) {
                psLock.setString(1, train.getTrainNumber());
                try (ResultSet rs = psLock.executeQuery()) {
                    while (rs.next()) activeSeatNumbers.add(rs.getString("seat_number"));
                }
            }

            List<Seat> availableSeats = new ArrayList<>();
            for (Seat s : train.getSeats()) {
                if (!activeSeatNumbers.contains(s.getSeatNumber())) availableSeats.add(s);
            }

            if (availableSeats.size() < numSeats) {
                conn.rollback();
                throw new ValidationException("Not enough seats available. Requested " + numSeats + ", available " + availableSeats.size());
            }

          String insertTicketSql = "INSERT INTO tickets(pnr, username, train_number, seat_number, travel_date, booked_by, status) VALUES(?,?,?,?,?,?,?)";
            String insertHistorySql = "INSERT INTO user_history(user_id, pnr, action, details) VALUES(?,?,?,?)";

          try (PreparedStatement psTicket = conn.prepareStatement(insertTicketSql);
              PreparedStatement psHistory = conn.prepareStatement(insertHistorySql);
              PreparedStatement psFind = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {

                for (int i = 0; i < numSeats; i++) {
                    String username = usernames.get(i);
                    if (username == null || username.isBlank()) username = "";
                    String pnr = PnrGenerator.generate();
                    Seat seatToBook = availableSeats.get(i);

                    psTicket.setString(1, pnr);
                    psTicket.setString(2, username);
                    psTicket.setString(3, train.getTrainNumber());
                    psTicket.setString(4, seatToBook.getSeatNumber());
                    psTicket.setString(5, date);
                    psTicket.setString(6, bookedBy);
                    psTicket.setString(7, "ACTIVE");
                    psTicket.executeUpdate();

                    Integer userId = null;
                    try {
                        psFind.setString(1, username);
                        try (ResultSet rs = psFind.executeQuery()) {
                            if (rs.next()) userId = rs.getInt("id");
                        }
                    } catch (SQLException ignored) {
                    }

                    try {
                        if (userId != null) psHistory.setInt(1, userId); else psHistory.setNull(1, java.sql.Types.INTEGER);
                        psHistory.setString(2, pnr);
                        psHistory.setString(3, "BOOK");
                        psHistory.setString(4, "Booked seat " + seatToBook.getSeatNumber() + " on train " + train.getTrainNumber() + " for user " + username);
                        psHistory.executeUpdate();
                    } catch (SQLException he) {
                        try (PreparedStatement psLegacy = conn.prepareStatement("INSERT INTO user_history(username, action, details) VALUES(?,?,?)")) {
                            psLegacy.setString(1, username);
                            psLegacy.setString(2, "BOOK");
                            psLegacy.setString(3, "Booked seat " + seatToBook.getSeatNumber() + " on train " + train.getTrainNumber() + " for user " + username);
                            psLegacy.executeUpdate();
                        } catch (SQLException ignored) {
                        }
                    }

                    User u = new User(username, "", Role.PASSENGER);
                    Ticket t = new Ticket(pnr, u, train, seatToBook, date);
                    created.add(t);
                }
            }

            conn.commit();

            for (Ticket t : created) {
                for (Seat s : train.getSeats()) {
                    if (s.getSeatNumber().equalsIgnoreCase(t.getSeat().getSeatNumber())) {
                        s.book();
                        break;
                    }
                }
                this.allTickets.add(t);
            }

            return created;
        } catch (SQLException | com.booking.exception.DatabaseException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { }
            throw new com.booking.exception.DatabaseException("Failed to create tickets transactionally", e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) { }
            }
        }
    }
public List<Ticket> findTicketsByPassenger(User passenger) {
    List<Ticket> passengerTickets = new ArrayList<>();
    String sqlBookedBy = "SELECT pnr, train_number, seat_number, travel_date, username, status FROM tickets WHERE booked_by = ? AND status = 'ACTIVE'";

    try (Connection c = this.db.getConnection()) {
        boolean usedBookedBy = true;
        PreparedStatement ps;
        try {
            ps = c.prepareStatement(sqlBookedBy);
            ps.setString(1, passenger.getUsername());
        } catch (SQLException se) {
            usedBookedBy = false;
            String sql = "SELECT pnr, train_number, seat_number, travel_date, status FROM tickets WHERE username = ? AND status = 'ACTIVE'";
            ps = c.prepareStatement(sql);
            ps.setString(1, passenger.getUsername());
        }

        try (PreparedStatement psFinal = ps; ResultSet rs = psFinal.executeQuery()) {
            while (rs.next()) {
                String pnr = rs.getString("pnr");
                String trainNumber = rs.getString("train_number");
                String seatNumber = rs.getString("seat_number");
                String travelDate = rs.getString("travel_date");
                String username = usedBookedBy ? rs.getString("username") : passenger.getUsername();
                String status = rs.getString("status");

                LocalDate travel = null;
                if (travelDate != null && travelDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    try {
                        travel = LocalDate.parse(travelDate, DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (DateTimeParseException ex) {
                        System.out.println("[WARN] Skipping malformed date for ticket " + pnr + ": " + travelDate);
                        continue;
                    }
                } else {
                    System.out.println("[WARN] Ignoring invalid travel_date for ticket " + pnr + ": " + travelDate);
                    continue;
                }

                if (travel.isBefore(LocalDate.now())) {
                    continue;
                }

                Train foundTrain = null;
                Seat foundSeat = null;
                for (Train t : trainService.getAllTrains()) {
                    if (t.getTrainNumber().equalsIgnoreCase(trainNumber)) {
                        foundTrain = t;
                        for (Seat s : t.getSeats()) {
                            if (s.getSeatNumber().equalsIgnoreCase(seatNumber)) {
                                foundSeat = s;
                                break;
                            }
                        }
                        break;
                    }
                }

                if (foundTrain != null && foundSeat != null && "ACTIVE".equalsIgnoreCase(status)) {
                    User u = new User(username, "", Role.PASSENGER);
                    Ticket tkt = new Ticket(pnr, u, foundTrain, foundSeat, travelDate);
                    passengerTickets.add(tkt);
                } else {
                    System.out.println("Warning: Could not resolve train/seat for active ticket " + pnr);
                }
            }
        }
    } catch (SQLException | com.booking.exception.DatabaseException e) {
        System.out.println("Error loading active tickets from DB: " + e.getMessage());
    }

    return passengerTickets;
}


    public Ticket findTicketByPnr(String pnr) {
        for (Ticket ticket : allTickets) {
            if (ticket.getPnrNumber().equalsIgnoreCase(pnr)) {
                return ticket;
            }
        }
        return null;
    }

    public boolean cancelTicket(Ticket ticket) {
        String sql = "UPDATE tickets SET status = 'CANCELLED' WHERE pnr = ?";
        try (Connection c = this.db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ticket.getPnrNumber());
            int updated = ps.executeUpdate();
            if (updated > 0) {
                try {
                    ticket.getSeat().unbook();
                } catch (Exception ignore) {
                }
                try {
                    this.allTickets.remove(ticket);
                } catch (Exception ignore) {
                }
                String findId = "SELECT id FROM users WHERE username = ?";
                Integer userId = null;
                try (Connection conn2 = this.db.getConnection(); PreparedStatement ps2 = conn2.prepareStatement(findId)) {
                    ps2.setString(1, ticket.getPassenger().getUsername());
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) userId = rs2.getInt("id");
                    }
                } catch (SQLException | com.booking.exception.DatabaseException e) {
                }

                String insertHistory = "INSERT INTO user_history(user_id, pnr, username, action, details) VALUES(?,?,?,?,?)";
                try (Connection conn3 = this.db.getConnection(); PreparedStatement ps3 = conn3.prepareStatement(insertHistory)) {
                    if (userId != null) ps3.setInt(1, userId); else ps3.setNull(1, java.sql.Types.INTEGER);
                    ps3.setString(2, ticket.getPnrNumber());
                    ps3.setString(3, ticket.getPassenger().getUsername());
                    ps3.setString(4, "CANCEL");
                    ps3.setString(5, "Cancelled ticket PNR " + ticket.getPnrNumber());
                    ps3.executeUpdate();
                } catch (SQLException | com.booking.exception.DatabaseException e) {
                }

                return true;
            } else {
                return false;
            }
        } catch (com.booking.exception.DatabaseException | SQLException e) {
            System.out.println("Error cancelling ticket in DB: " + e.getMessage());
            return false;
        }
    }

    public List<Ticket> getAllTickets() {
        return this.allTickets;
    }

    private void loadActiveTicketsFromDb() {
        String sql = "SELECT pnr, username, train_number, seat_number, travel_date FROM tickets WHERE status = 'ACTIVE'";
        try (Connection c = this.db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String pnr = rs.getString("pnr");
                String username = rs.getString("username");
                String trainNumber = rs.getString("train_number");
                String seatNumber = rs.getString("seat_number");
                String travelDate = rs.getString("travel_date");

                Train foundTrain = null;
                Seat foundSeat = null;
                for (Train t : trainService.getAllTrains()) {
                    if (t.getTrainNumber().equalsIgnoreCase(trainNumber)) {
                        foundTrain = t;
                        for (Seat s : t.getSeats()) {
                            if (s.getSeatNumber().equalsIgnoreCase(seatNumber)) {
                                foundSeat = s;
                                break;
                            }
                        }
                        break;
                    }
                }

                if (foundTrain != null && foundSeat != null) {
                    User u = new User(username, "", Role.PASSENGER);
                    Ticket tkt = new Ticket(pnr, u, foundTrain, foundSeat, travelDate);
                    this.allTickets.add(tkt);
                    foundSeat.book();
                } else {
                    System.out.println("Warning: Could not resolve train/seat for ticket " + pnr);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error loading active tickets from DB: " + e.getMessage());
        }
    }

   public List<Ticket> findPastTicketsByPassenger(User passenger) {
    List<Ticket> past = new ArrayList<>();
    String sqlBookedBy = "SELECT pnr, username, train_number, seat_number, travel_date, status FROM tickets WHERE booked_by = ?";

    try (Connection c = this.db.getConnection()) {
        boolean usedBookedBy = true;
        PreparedStatement ps;
        try {
            ps = c.prepareStatement(sqlBookedBy);
            ps.setString(1, passenger.getUsername());
        } catch (SQLException se) {
            usedBookedBy = false;
            String sql = "SELECT pnr, username, train_number, seat_number, travel_date, status FROM tickets WHERE username = ?";
            ps = c.prepareStatement(sql);
            ps.setString(1, passenger.getUsername());
        }

        try (PreparedStatement psFinal = ps; ResultSet rs = psFinal.executeQuery()) {
            while (rs.next()) {
                String pnr = rs.getString("pnr");
                String username = rs.getString("username") != null ? rs.getString("username") : passenger.getUsername();
                String trainNumber = rs.getString("train_number");
                String seatNumber = rs.getString("seat_number");
                String travelDate = rs.getString("travel_date");
                String status = rs.getString("status");

                LocalDate travel = null;
                boolean isPast = false;
                if (travelDate != null && travelDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    try {
                        travel = LocalDate.parse(travelDate, DateTimeFormatter.ISO_LOCAL_DATE);
                        isPast = travel.isBefore(LocalDate.now());
                    } catch (DateTimeParseException ex) {
                        System.out.println("[WARN] Skipping invalid date for past ticket " + pnr + ": " + travelDate);
                        continue;
                    }
                } else {
                    System.out.println("[WARN] Ignoring non-ISO date for past ticket " + pnr + ": " + travelDate);
                    continue;
                }

                    boolean isActive = status != null && status.trim().equalsIgnoreCase("ACTIVE");
                    boolean isCancelled = status != null && status.trim().equalsIgnoreCase("CANCELLED");

                    // Only include tickets that are active and whose travel date is before today
                    if (isActive && isPast) {
                    Train foundTrain = null;
                    Seat foundSeat = null;
                    for (Train t : trainService.getAllTrains()) {
                        if (t.getTrainNumber().equalsIgnoreCase(trainNumber)) {
                            foundTrain = t;
                            for (Seat s : t.getSeats()) {
                                if (s.getSeatNumber().equalsIgnoreCase(seatNumber)) {
                                    foundSeat = s;
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    if (foundTrain != null && foundSeat != null) {
                        User u = new User(username, "", Role.PASSENGER);
                        Ticket tkt = new Ticket(pnr, u, foundTrain, foundSeat, travelDate);
                        past.add(tkt);
                    } else {
                        System.out.println("Warning: Could not resolve train/seat for past ticket " + pnr);
                    }
                }
            }
        }
    } catch (SQLException | com.booking.exception.DatabaseException e) {
        System.out.println("Error loading past tickets from DB: " + e.getMessage());
    }

    return past;
}
    
    public List<Ticket> findCancelledTicketsByPassenger(User passenger) {
        List<Ticket> cancelled = new ArrayList<>();
        String sqlBookedBy = "SELECT pnr, username, train_number, seat_number, travel_date, status FROM tickets WHERE booked_by = ? AND status = 'CANCELLED'";
        try (Connection c = this.db.getConnection()) {
            boolean usedBookedBy = true;
            PreparedStatement ps;
            try {
                ps = c.prepareStatement(sqlBookedBy);
                ps.setString(1, passenger.getUsername());
            } catch (SQLException se) {
                usedBookedBy = false;
                String sql = "SELECT pnr, username, train_number, seat_number, travel_date, status FROM tickets WHERE username = ? AND status = 'CANCELLED'";
                ps = c.prepareStatement(sql);
                ps.setString(1, passenger.getUsername());
            }

            try (PreparedStatement psFinal = ps; ResultSet rs = psFinal.executeQuery()) {
                while (rs.next()) {
                    String pnr = rs.getString("pnr");
                    String username = null;
                    try { username = rs.getString("username"); } catch (SQLException ignore) { username = passenger.getUsername(); }
                    String trainNumber = rs.getString("train_number");
                    String seatNumber = rs.getString("seat_number");
                    String travelDate = rs.getString("travel_date");

                    Train foundTrain = null;
                    Seat foundSeat = null;
                    for (Train t : trainService.getAllTrains()) {
                        if (t.getTrainNumber().equalsIgnoreCase(trainNumber)) {
                            foundTrain = t;
                            for (Seat s : t.getSeats()) {
                                if (s.getSeatNumber().equalsIgnoreCase(seatNumber)) {
                                    foundSeat = s;
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    if (foundTrain != null && foundSeat != null) {
                        User u = new User(username != null ? username : passenger.getUsername(), "", Role.PASSENGER);
                        Ticket tkt = new Ticket(pnr, u, foundTrain, foundSeat, travelDate);
                        cancelled.add(tkt);
                    } else {
                        System.out.println("Warning: Could not resolve train/seat for cancelled ticket " + pnr);
                    }
                }
            }
        } catch (SQLException | com.booking.exception.DatabaseException e) {
            System.out.println("Error loading cancelled tickets from DB: " + e.getMessage());
        }
        return cancelled;
    }
}