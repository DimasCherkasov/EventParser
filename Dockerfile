FROM openjdk:11-jdk-slim

WORKDIR /app

# Copy the JAR file
COPY target/event-parser-1.0-SNAPSHOT.jar app.jar

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=prod

# Expose the port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]