package com.eventparser.scheduler;

import com.eventparser.model.Event;
import com.eventparser.parser.EventBriteParser;
import com.eventparser.parser.EventParser;
import com.eventparser.parser.KudaGoApiParser;
import com.eventparser.parser.TimePadLargeEventsParser;
import com.eventparser.parser.WebsiteEventParser;
import com.eventparser.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Scheduler for periodically parsing events from configured sources.
 */
@Slf4j
@Component
public class EventParsingScheduler {

    private final WebsiteEventParser websiteEventParser;
    private final KudaGoApiParser kudaGoApiParser;
    private final EventBriteParser eventBriteParser;
    private final TimePadLargeEventsParser timePadLargeEventsParser;
    private final EventService eventService;

    @Value("${parser.sources:}")
    private String parserSources;

    @Autowired
    public EventParsingScheduler(WebsiteEventParser websiteEventParser, 
                                KudaGoApiParser kudaGoApiParser,
                                EventBriteParser eventBriteParser,
                                TimePadLargeEventsParser timePadLargeEventsParser,
                                EventService eventService) {
        this.websiteEventParser = websiteEventParser;
        this.kudaGoApiParser = kudaGoApiParser;
        this.eventBriteParser = eventBriteParser;
        this.timePadLargeEventsParser = timePadLargeEventsParser;
        this.eventService = eventService;
        log.info("Initialized EventParsingScheduler with all parsers");
    }

    /**
     * Parse events from all configured sources every hour.
     * The cron expression "0 0 * * * *" means "at minute 0 of every hour".
     */
    @Scheduled(cron = "0 0 * * * *")
    public void parseEventsHourly() {
        log.info("Starting scheduled event parsing");
        List<String> sources = getParserSources();

        if (sources.isEmpty()) {
            log.warn("No parser sources configured. Set parser.sources in application.properties.");
            return;
        }

        int totalParsed = parseEventsFromSources(sources);
        log.info("Completed scheduled event parsing. Parsed {} events from {} sources", totalParsed, sources.size());
    }

    /**
     * Parse events from all configured sources.
     *
     * @param sources The list of sources to parse events from
     * @return The total number of events parsed and saved
     */
    public int parseEventsFromSources(List<String> sources) {
        List<CompletableFuture<List<Event>>> futures = new ArrayList<>();

        // Для каждого источника выбираем подходящий парсер
        for (String source : sources) {
            CompletableFuture<List<Event>> future;

            // Если источник начинается с "api:", выбираем соответствующий API-парсер
            if (source.startsWith("api:")) {
                String apiSource = source.substring(4); // Удаляем префикс "api:"

                if (apiSource.startsWith("timepad")) {
                    // Используем TimePadLargeEventsParser для API TimePad
                    log.info("Using TimePadLargeEventsParser for API source: {}", source);
                    future = timePadLargeEventsParser.parseEventsAsync(source);
                } else {
                    // Используем KudaGoApiParser для других API
                    log.info("Using KudaGoApiParser for API source: {}", apiSource);
                    future = kudaGoApiParser.parseEventsAsync(apiSource);
                }
            } else if (source.contains("eventbrite.com")) {
                // Используем EventBriteParser для EventBrite
                log.info("Using EventBriteParser for source: {}", source);
                future = eventBriteParser.parseEventsAsync(source);
            } else if (source.contains("timepad.ru")) {
                // Используем TimePadLargeEventsParser для TimePad
                log.info("Using TimePadLargeEventsParser for source: {}", source);
                future = timePadLargeEventsParser.parseEventsAsync(source);
            } else {
                // Для остальных источников используем WebsiteEventParser
                log.info("Using WebsiteEventParser for source: {}", source);
                future = websiteEventParser.parseEventsAsync(source);
            }

            futures.add(future);
        }

        List<Event> allEvents = new ArrayList<>();

        // Wait for all parsing to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect all parsed events
        for (CompletableFuture<List<Event>> future : futures) {
            try {
                List<Event> events = future.get();
                allEvents.addAll(events);
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error getting parsed events: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }

        // Save all events to the database
        int savedCount = eventService.saveEvents(allEvents);
        log.info("Saved {} new events out of {} parsed events", savedCount, allEvents.size());

        return savedCount;
    }

    /**
     * Get the list of parser sources from the configuration.
     *
     * @return The list of parser sources
     */
    public List<String> getParserSources() {
        if (parserSources == null || parserSources.isEmpty()) {
            return List.of();
        }

        return Arrays.stream(parserSources.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
