package com.eventparser.service;

import com.eventparser.model.Event;
import com.eventparser.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    /**
     * Save a list of events to the database.
     * If an event with the same name, date, and location already exists, it will be skipped.
     *
     * @param events The list of events to save
     * @return The number of events saved
     */
    @Transactional
    public int saveEvents(List<Event> events) {
        int savedCount = 0;

        for (Event event : events) {
            // Check if event already exists
            List<Event> existingEvents = eventRepository.findByNameContainingIgnoreCase(event.getName());
            boolean exists = existingEvents.stream()
                    .anyMatch(e -> e.getDate().equals(event.getDate()) && 
                                  e.getLocation().equalsIgnoreCase(event.getLocation()));

            if (!exists) {
                eventRepository.save(event);
                savedCount++;
                log.info("Saved new event: {} at {} on {}", event.getName(), event.getLocation(), event.getDate());
            } else {
                log.info("Skipped duplicate event: {} at {} on {}", event.getName(), event.getLocation(), event.getDate());
            }
        }

        return savedCount;
    }

    /**
     * Get all events.
     *
     * @return List of all events
     */
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    /**
     * Get events that have not had messages sent to their organizers.
     *
     * @return List of events where message_sent is false
     */
    public List<Event> getEventsWithoutMessages() {
        return eventRepository.findByMessageSentFalse();
    }

    /**
     * Get events that have had messages sent but no response received.
     *
     * @return List of events where message_sent is true and response_received is false
     */
    public List<Event> getEventsAwaitingResponse() {
        return eventRepository.findByMessageSentTrueAndResponseReceivedFalse();
    }

    /**
     * Get events by location.
     *
     * @param location The location to search for
     * @return List of events at the specified location
     */
    public List<Event> getEventsByLocation(String location) {
        return eventRepository.findByLocationContainingIgnoreCase(location);
    }

    /**
     * Get events occurring after a specific date.
     *
     * @param date The date after which to find events
     * @return List of events occurring after the specified date
     */
    public List<Event> getEventsByDateAfter(LocalDateTime date) {
        return eventRepository.findByDateAfter(date);
    }

    /**
     * Get events by name containing the search term (case insensitive).
     *
     * @param name The name to search for
     * @return List of events with names containing the search term
     */
    public List<Event> getEventsByName(String name) {
        return eventRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Get events by organizer contact.
     *
     * @param contact The organizer contact to search for
     * @return List of events with the specified organizer contact
     */
    public List<Event> getEventsByOrganizerContact(String contact) {
        return eventRepository.findByOrganizerContactContainingIgnoreCase(contact);
    }

    /**
     * Get upcoming events (within the next 7 days).
     *
     * @return List of events happening within the next 7 days
     */
    public List<Event> getUpcomingEvents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusDays(7);
        return eventRepository.findUpcomingEvents(now, endDate);
    }

    /**
     * Get an event by ID.
     *
     * @param id The ID of the event to get
     * @return Optional containing the event if found, empty otherwise
     */
    public Optional<Event> getEventById(Long id) {
        return eventRepository.findById(id);
    }

    /**
     * Update an event.
     *
     * @param event The event to update
     * @return The updated event
     */
    @Transactional
    public Event updateEvent(Event event) {
        return eventRepository.save(event);
    }

    /**
     * Delete an event.
     *
     * @param id The ID of the event to delete
     */
    @Transactional
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }

    /**
     * Mark an event as having had a message sent.
     *
     * @param id The ID of the event to update
     * @return The updated event
     */
    @Transactional
    public Event markMessageSent(Long id) {
        Optional<Event> eventOpt = eventRepository.findById(id);
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            event.setMessageSent(true);
            return eventRepository.save(event);
        } else {
            throw new IllegalArgumentException("Event not found with ID: " + id);
        }
    }

    /**
     * Mark an event as having received a response.
     *
     * @param id The ID of the event to update
     * @return The updated event
     */
    @Transactional
    public Event markResponseReceived(Long id) {
        Optional<Event> eventOpt = eventRepository.findById(id);
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            event.setResponseReceived(true);
            return eventRepository.save(event);
        } else {
            throw new IllegalArgumentException("Event not found with ID: " + id);
        }
    }

    /**
     * Clear all events from the database.
     * 
     * @return The number of events deleted
     */
    @Transactional
    public int clearAllEvents() {
        int count = eventRepository.findAll().size();
        eventRepository.deleteAll();
        log.info("Cleared all {} events from the database", count);
        return count;
    }
}
