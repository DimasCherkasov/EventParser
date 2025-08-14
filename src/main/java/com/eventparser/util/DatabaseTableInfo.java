package com.eventparser.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility to check database tables and their structure.
 */
public class DatabaseTableInfo {

    public static void main(String[] args) {
        // Database connection parameters
        String url = "jdbc:postgresql://localhost:5432/eventparser";
        String username = "postgres";
        String password = "postgres";

        System.out.println("Checking database tables in PostgreSQL...");

        try {
            // Load the PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");

            // Attempt to establish a connection
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                System.out.println("Connection successful!");
                
                // Get database metadata
                DatabaseMetaData metaData = connection.getMetaData();
                
                // Get tables
                ResultSet tables = metaData.getTables(null, "public", "%", new String[]{"TABLE"});
                
                if (!tables.isBeforeFirst()) {
                    System.out.println("No tables found in the database!");
                } else {
                    System.out.println("Tables in the database:");
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        System.out.println("- " + tableName);
                        
                        // Get columns for this table
                        ResultSet columns = metaData.getColumns(null, "public", tableName, "%");
                        System.out.println("  Columns:");
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            String columnType = columns.getString("TYPE_NAME");
                            System.out.println("  - " + columnName + " (" + columnType + ")");
                        }
                        
                        // Get row count
                        try (Statement stmt = connection.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                            if (rs.next()) {
                                int rowCount = rs.getInt(1);
                                System.out.println("  Row count: " + rowCount);
                            }
                        } catch (SQLException e) {
                            System.err.println("Error getting row count for table " + tableName + ": " + e.getMessage());
                        }
                        
                        System.out.println();
                    }
                }
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