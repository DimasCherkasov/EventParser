# Event Parser

A Java application for parsing events from open sources (websites, social networks), processing the data, and saving it to PostgreSQL.

## Features

- **Event Parsing**
  - Parse events from websites using Jsoup
  - Extract event details (name, date, location, price, participants count, organizer contacts)
  - Multi-threaded parsing for improved performance

- **Message Sending**
  - Automatic message sending to event organizers via:
    - Email (JavaMail)
    - Telegram (Telegram Bot API)
  - Logging of message status (success/failure)

- **PostgreSQL Integration**
  - Store events with all relevant details
  - Track message sending status and responses

- **Telegram Bot**
  - Send messages to organizers
  - Track responses and update database

## Technology Stack

- Java 11
- Spring Boot 2.7
- PostgreSQL + Hibernate/JPA
- Jsoup (HTML parsing)
- Telegram Bot API
- JavaMail
- Maven

## Setup

### Prerequisites

- JDK 11+
- Maven
- PostgreSQL database
- Telegram Bot (for Telegram integration)

### Database Setup

1. Create a PostgreSQL database:
```sql
CREATE DATABASE eventparser;
```

2. The application will automatically create the necessary tables on startup.

### Configuration

Edit `src/main/resources/application.properties` to configure:

- Database connection
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/eventparser
spring.datasource.username=your_username
spring.datasource.password=your_password
```

- Email settings
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

- Telegram Bot
```properties
telegram.bot.username=your_bot_username
telegram.bot.token=your_bot_token
```

- Parser settings
```properties
parser.sources=https://www.meetup.com/find/events/, https://www.eventbrite.com/d/online/all-events/
```

### Building the Application

```bash
mvn clean package
```

### Running the Application

```bash
java -jar target/event-parser-1.0-SNAPSHOT.jar
```

### Docker Deployment

Build the Docker image:
```bash
docker build -t event-parser .
```

Run the container:
```bash
docker run -p 8080:8080 event-parser
```

## Usage

The application runs automatically based on the configured schedule:

1. Events are parsed hourly from the configured sources
2. New events are saved to the database
3. Messages can be sent to event organizers via the configured channels
4. Responses are tracked and recorded in the database

## Extending the Application

### Adding New Parser Sources

To add support for parsing events from a new website:

1. Update the `parser.sources` property in `application.properties`
2. If needed, customize the `WebsiteEventParser` class to handle the specific HTML structure of the new website

### Adding New Message Channels

To add support for a new message channel:

1. Create a new class implementing the `MessageSender` interface
2. Implement the required methods for sending messages through the new channel
3. The application will automatically use the new channel when appropriate

## License

This project is licensed under the MIT License - see the LICENSE file for details.