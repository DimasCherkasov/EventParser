package com.eventparser.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Simple utility to test the database connection.
 */
public class DatabaseConnectionTest {

    public static void main(String[] args) {
        // Database connection parameters
        String url = "jdbc:postgresql://localhost:5432/eventparser";
        String username = "postgres";
        String password = "postgres";

        System.out.println("Testing connection to PostgreSQL database...");

        try {
            // Load the PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");

            // Attempt to establish a connection
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                System.out.println("Connection successful!");
                System.out.println("Database product name: " + connection.getMetaData().getDatabaseProductName());
                System.out.println("Database product version: " + connection.getMetaData().getDatabaseProductVersion());
            }
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Connection failed!");
            System.err.println("Error message: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace();
        }
    }
}