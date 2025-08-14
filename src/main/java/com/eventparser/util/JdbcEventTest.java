package com.eventparser.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Simple JDBC test for the events table.
 */
public class JdbcEventTest {

    public static void main(String[] args) {
        // Database connection parameters
        String url = "jdbc:postgresql://localhost:5432/eventparser";
        String username = "postgres";
        String password = "postgres";

        System.out.println("Testing JDBC operations on events table...");

        try {
            // Load the PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");

            // Attempt to establish a connection
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                System.out.println("Connection successful!");

                // Count events
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM events")) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        System.out.println("Total events in database: " + count);
                    }
                }

                // Get all events
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM events LIMIT 5")) {
                    System.out.println("\nEvents in database:");
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        long id = rs.getLong("id");
                        String name = rs.getString("name");
                        Timestamp date = rs.getTimestamp("date");
                        String location = rs.getString("location");
                        System.out.println("Event #" + count + ": ID=" + id + ", Name=" + name + 
                                          ", Date=" + date + ", Location=" + location);
                    }
                    if (count == 0) {
                        System.out.println("No events found in the database!");
                    }
                }

                // Insert a test event
                String insertSql = "INSERT INTO events (name, date, location, organizer_contact, message_sent, response_received, created_at) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
                try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                    pstmt.setString(1, "JDBC Test Event");
                    pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
                    pstmt.setString(3, "JDBC Test Location");
                    pstmt.setString(4, "jdbc@test.com");
                    pstmt.setBoolean(5, false);
                    pstmt.setBoolean(6, false);
                    pstmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    
                    ResultSet rs = pstmt.executeQuery();
                    long newId = -1;
                    if (rs.next()) {
                        newId = rs.getLong(1);
                        System.out.println("\nInserted test event with ID: " + newId);
                    }
                    
                    // Delete the test event
                    if (newId != -1) {
                        try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM events WHERE id = ?")) {
                            deleteStmt.setLong(1, newId);
                            int rowsDeleted = deleteStmt.executeUpdate();
                            System.out.println("Deleted " + rowsDeleted + " test event(s)");
                        }
                    }
                }

                System.out.println("\nJDBC test completed successfully!");
            }
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace();
        }
    }
}