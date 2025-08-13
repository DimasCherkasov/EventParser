package com.eventparser.service;

import com.eventparser.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for sending messages to event organizers.
 */
@Slf4j
@Service
public class MessageService {

    private final List<MessageSender> messageSenders;
    private final EventService eventService;

    public MessageService(List<MessageSender> messageSenders, EventService eventService) {
        this.messageSenders = messageSenders;
        this.eventService = eventService;
    }

    /**
     * Send a message to an event organizer.
     *
     * @param eventId The ID of the event
     * @param message The message content
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessage(Long eventId, String message) {
        Optional<Event> eventOpt = eventService.getEventById(eventId);
        if (eventOpt.isEmpty()) {
            log.error("Cannot send message: Event not found with ID: {}", eventId);
            return false;
        }

        Event event = eventOpt.get();
        return sendMessage(event, message);
    }

    /**
     * Send a message to an event organizer.
     *
     * @param event The event
     * @param message The message content
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessage(Event event, String message) {
        String contact = event.getOrganizerContact();
        if (contact == null || contact.isEmpty()) {
            log.error("Cannot send message: Event has no organizer contact: {}", event.getId());
            return false;
        }

        // Find a message sender that can handle this contact
        Optional<MessageSender> senderOpt = messageSenders.stream()
                .filter(sender -> sender.canHandle(contact))
                .findFirst();

        if (senderOpt.isEmpty()) {
            log.error("Cannot send message: No message sender available for contact: {}", contact);
            return false;
        }

        MessageSender sender = senderOpt.get();
        boolean success = sender.sendMessage(event, message);

        if (success) {
            // Update the event to mark the message as sent
            eventService.markMessageSent(event.getId());
            log.info("Message sent successfully to organizer of event: {}", event.getName());
        }

        return success;
    }

    /**
     * Send a message to an event organizer asynchronously.
     *
     * @param eventId The ID of the event
     * @param message The message content
     * @return A CompletableFuture that will complete with true if the message was sent successfully, false otherwise
     */
    public CompletableFuture<Boolean> sendMessageAsync(Long eventId, String message) {
        Optional<Event> eventOpt = eventService.getEventById(eventId);
        if (eventOpt.isEmpty()) {
            log.error("Cannot send message asynchronously: Event not found with ID: {}", eventId);
            return CompletableFuture.completedFuture(false);
        }

        Event event = eventOpt.get();
        return sendMessageAsync(event, message);
    }

    /**
     * Send a message to an event organizer asynchronously.
     *
     * @param event The event
     * @param message The message content
     * @return A CompletableFuture that will complete with true if the message was sent successfully, false otherwise
     */
    public CompletableFuture<Boolean> sendMessageAsync(Event event, String message) {
        String contact = event.getOrganizerContact();
        if (contact == null || contact.isEmpty()) {
            log.error("Cannot send message asynchronously: Event has no organizer contact: {}", event.getId());
            return CompletableFuture.completedFuture(false);
        }

        // Find a message sender that can handle this contact
        Optional<MessageSender> senderOpt = messageSenders.stream()
                .filter(sender -> sender.canHandle(contact))
                .findFirst();

        if (senderOpt.isEmpty()) {
            log.error("Cannot send message asynchronously: No message sender available for contact: {}", contact);
            return CompletableFuture.completedFuture(false);
        }

        MessageSender sender = senderOpt.get();
        return sender.sendMessageAsync(event, message)
                .thenApply(success -> {
                    if (success) {
                        // Update the event to mark the message as sent
                        eventService.markMessageSent(event.getId());
                        log.info("Message sent asynchronously to organizer of event: {}", event.getName());
                    }
                    return success;
                });
    }

    /**
     * Send messages to all events that have not had messages sent yet.
     *
     * @param message The message content
     * @return The number of messages sent successfully
     */
    public int sendMessagesToEventsWithoutMessages(String message) {
        List<Event> events = eventService.getEventsWithoutMessages();
        int successCount = 0;

        for (Event event : events) {
            boolean success = sendMessage(event, message);
            if (success) {
                successCount++;
            }
        }

        log.info("Sent messages to {} out of {} events", successCount, events.size());
        return successCount;
    }

    /**
     * Send messages asynchronously to all events that have not had messages sent yet.
     *
     * @param message The message content
     * @return A CompletableFuture that will complete with the number of messages sent successfully
     */
    public CompletableFuture<Integer> sendMessagesToEventsWithoutMessagesAsync(String message) {
        List<Event> events = eventService.getEventsWithoutMessages();

        // Create a CompletableFuture for each message send operation
        List<CompletableFuture<Boolean>> futures = events.stream()
                .map(event -> sendMessageAsync(event, message))
                .collect(Collectors.toList());

        // Combine all futures into one that completes when all are done
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    // Count the number of successful sends
                    long successCount = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Boolean::booleanValue)
                            .count();

                    log.info("Sent messages asynchronously to {} out of {} events", successCount, events.size());
                    return (int) successCount;
                });
    }
}
