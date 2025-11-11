package com.booking.service;

import java.sql.Connection;

public interface DatabaseProvider {
    Connection getConnection();

    void init();
}