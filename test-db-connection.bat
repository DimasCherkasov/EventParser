@echo off
echo Testing connection to PostgreSQL database...
java -cp "src\main\java;postgresql-42.3.6.jar" com.eventparser.util.DatabaseConnectionTest
echo.
echo If the connection was successful, your application is correctly configured to connect to PostgreSQL.
echo.
echo To build and run the full application, you need to:
echo 1. Install Maven from https://maven.apache.org/download.cgi
echo 2. Run 'mvn clean package' to build the application
echo 3. Run 'java -jar target\event-parser-1.0-SNAPSHOT.jar' to start the application
echo.
echo Alternatively, you can open the project in an IDE like IntelliJ IDEA or Eclipse and run it from there.
echo.
pause