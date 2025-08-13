package com.eventparser.service;

import com.eventparser.model.Event;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for sending messages to event organizers.
 */
public interface MessageSender {

    /**
     * Send a message to an event organizer.
     *
     * @param event The event to send a message about
     * @param message The message content
     * @return true if the message was sent successfully, false otherwise
     */
    boolean sendMessage(Event event, String message);

    /**
     * Send a message to an event organizer asynchronously.
     *
     * @param event The event to send a message about
     * @param message The message content
     * @return A CompletableFuture that will complete with true if the message was sent successfully, false otherwise
     */
    CompletableFuture<Boolean> sendMessageAsync(Event event, String message);

    /**
     * Check if this message sender can handle the given contact information.
     *
     * @param contact The contact information to check
     * @return true if this message sender can handle the contact, false otherwise
     */
    boolean canHandle(String contact);
}