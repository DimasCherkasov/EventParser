package com.eventparser.util;

import com.eventparser.model.Event;
import com.eventparser.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Test application to verify EventRepository functionality.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.eventparser")
public class EventRepositoryTest {

    public static void main(String[] args) {
        SpringApplication.run(EventRepositoryTest.class, args);
    }

    @Bean
    public CommandLineRunner testRepository(@Autowired EventRepository eventRepository) {
        return args -> {
            System.out.println("Testing EventRepository...");
            
            // Count events
            long count = eventRepository.count();
            System.out.println("Total events in database: " + count);
            
            // Find all events
            List<Event> allEvents = eventRepository.findAll();
            System.out.println("Found " + allEvents.size() + " events");
            
            // Print first 5 events
            int limit = Math.min(5, allEvents.size());
            for (int i = 0; i < limit; i++) {
                Event event = allEvents.get(i);
                System.out.println("Event #" + (i+1) + ": " + event.getName() + " at " + event.getLocation() + " on " + event.getDate());
            }
            
            // Create a test event
            Event testEvent = Event.builder()
                    .name("Test Event for Repository Test")
                    .date(LocalDateTime.now().plusDays(1))
                    .location("Test Location")
                    .price(new BigDecimal("100.00"))
                    .organizerName("Test Organizer")
                    .organizerContact("test@example.com")
                    .sourceUrl("http://example.com/test")
                    .build();
            
            // Save the test event
            Event savedEvent = eventRepository.save(testEvent);
            System.out.println("Saved test event with ID: " + savedEvent.getId());
            
            // Find the test event by ID
            Event foundEvent = eventRepository.findById(savedEvent.getId()).orElse(null);
            if (foundEvent != null) {
                System.out.println("Found test event by ID: " + foundEvent.getName());
            } else {
                System.out.println("Test event not found by ID!");
            }
            
            // Find events by name
            List<Event> eventsByName = eventRepository.findByNameContainingIgnoreCase("Test");
            System.out.println("Found " + eventsByName.size() + " events with 'Test' in name");
            
            // Find upcoming events
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endDate = now.plusDays(7);
            List<Event> upcomingEvents = eventRepository.findUpcomingEvents(now, endDate);
            System.out.println("Found " + upcomingEvents.size() + " upcoming events in the next 7 days");
            
            // Delete the test event
            eventRepository.deleteById(savedEvent.getId());
            System.out.println("Deleted test event");
            
            // Verify deletion
            boolean exists = eventRepository.existsById(savedEvent.getId());
            System.out.println("Test event still exists: " + exists);
            
            System.out.println("EventRepository test completed successfully!");
        };
    }
}