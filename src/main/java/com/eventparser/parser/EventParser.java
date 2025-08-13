package com.eventparser.parser;

import com.eventparser.model.Event;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for parsing events from different sources.
 */
public interface EventParser {

    /**
     * Parse events from a source.
     *
     * @param source The source URL or identifier to parse events from
     * @return A list of parsed events
     */
    List<Event> parseEvents(String source);

    /**
     * Parse events asynchronously from a source.
     *
     * @param source The source URL or identifier to parse events from
     * @return A CompletableFuture that will complete with the list of parsed events
     */
    CompletableFuture<List<Event>> parseEventsAsync(String source);
}