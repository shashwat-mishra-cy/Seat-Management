package com.booking.service;

import com.booking.model.User;


public interface AuthProvider {

    /**
     * Register a new user with the given username and password.
     * @param username the username to register
     * @param password the password for the account
     * @return true if registration succeeded, false otherwise
     */
    boolean register(String username, String password);

    /**
     * Authenticate a user and return the User object if credentials are valid.
     * @return User on success or null on failure
     */
    User login(String username, String password);

    default void recordLogout(String username) {
    }
}