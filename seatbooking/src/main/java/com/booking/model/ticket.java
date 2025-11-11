package com.booking.model;

public class Ticket {

    private String pnrNumber; 
    private User passenger;
    private Train train;
    private Seat seat;
    private String travelDate;

    public Ticket(String pnrNumber, User passenger, Train train, Seat seat, String travelDate) {
        this.pnrNumber = pnrNumber;
        this.passenger = passenger;
        this.train = train;
        this.seat = seat;
        this.travelDate = travelDate;
    }

    public String getPnrNumber() {
        return pnrNumber;
    }

    public User getPassenger() {
        return passenger;
    }

    public Train getTrain() {
        return train;
    }

    public Seat getSeat() {
        return seat;
    }

    public String getTravelDate() {
        return travelDate;
    }


    public void displayTicketDetails() {
        System.out.println("---------------------------------");
        System.out.println(" PNR Number: " + pnrNumber);
        System.out.println(" Passenger: " + passenger.getUsername());
        System.out.println(" Train: " + train.getTrainName() + " (" + train.getTrainNumber() + ")");
        System.out.println(" Seat: " + seat.getSeatNumber());
        System.out.println(" Date: " + travelDate);
        System.out.println("---------------------------------");
    }
}