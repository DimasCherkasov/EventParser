# Connecting EventParser to PostgreSQL

This guide explains how to connect the EventParser application to a PostgreSQL database running in Docker.

## Prerequisites

- Docker installed and running
- PostgreSQL container running
- Java JDK 11 or higher installed

## Current Configuration

The application is configured to connect to a PostgreSQL database with the following settings:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/eventparser
spring.datasource.username=postgres
spring.datasource.password=postgres
```

These settings are defined in `src/main/resources/application.properties`.

## Testing the Database Connection

1. Run the `test-db-connection.bat` script to test the database connection:

```
.\test-db-connection.bat
```

Note: In PowerShell, you need to use the `.\` prefix to run scripts from the current directory.

If the connection is successful, you'll see a message like:

```
Testing connection to PostgreSQL database...
Connection successful!
Database product name: PostgreSQL
Database product version: 17.5 (Debian 17.5-1.pgdg130+1)
```

## Running the PostgreSQL Container

If the PostgreSQL container is not running, you can start it with:

```
docker start postgres-container
```

If you need to create a new PostgreSQL container:

```
docker run --name postgres-container -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -e POSTGRES_DB=eventparser -p 5432:5432 -v postgres-data:/var/lib/postgresql/data -d postgres
```

## Building and Running the Application

To build and run the full application:

1. Install Maven from https://maven.apache.org/download.cgi
2. Run `mvn clean package` to build the application
3. Run `java -jar target\event-parser-1.0-SNAPSHOT.jar` to start the application

Alternatively, you can open the project in an IDE like IntelliJ IDEA or Eclipse and run it from there.

## Troubleshooting

If you encounter connection issues:

1. Make sure the PostgreSQL container is running: `docker ps`
2. Check if the database exists: `docker exec -it postgres-container psql -U postgres -c "\l"`
3. Verify the connection settings in `application.properties`
4. Try connecting to the database manually: `docker exec -it postgres-container psql -U postgres -d eventparser`
