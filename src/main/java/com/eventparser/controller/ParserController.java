package com.eventparser.controller;

import com.eventparser.model.Event;
import com.eventparser.parser.EventParser;
import com.eventparser.parser.KudaGoApiParser;
import com.eventparser.parser.WebsiteEventParser;
import com.eventparser.parser.EventBriteParser;
import com.eventparser.parser.TimePadLargeEventsParser;
import com.eventparser.scheduler.EventParsingScheduler;
import com.eventparser.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * REST контроллер для управления парсингом мероприятий.
 * Предоставляет API для ручного запуска парсинга и получения информации о доступных источниках.
 */
@Slf4j
@RestController
@RequestMapping("/api/parser")
public class ParserController {

    private final WebsiteEventParser websiteEventParser;
    private final KudaGoApiParser kudaGoApiParser;
    private final EventBriteParser eventBriteParser;
    private final TimePadLargeEventsParser timePadLargeEventsParser;
    private final EventService eventService;
    private final EventParsingScheduler eventParsingScheduler;

    @Autowired
    public ParserController(WebsiteEventParser websiteEventParser,
                        KudaGoApiParser kudaGoApiParser,
                        EventBriteParser eventBriteParser,
                        TimePadLargeEventsParser timePadLargeEventsParser,
                        EventService eventService,
                        EventParsingScheduler eventParsingScheduler) {
        this.websiteEventParser = websiteEventParser;
        this.kudaGoApiParser = kudaGoApiParser;
        this.eventBriteParser = eventBriteParser;
        this.timePadLargeEventsParser = timePadLargeEventsParser;
        this.eventService = eventService;
        this.eventParsingScheduler = eventParsingScheduler;
    }

    /**
     * Получить список всех настроенных источников для парсинга.
     *
     * @return Список URL источников
     */
    @GetMapping("/sources")
    public ResponseEntity<List<String>> getParserSources() {
        List<String> sources = eventParsingScheduler.getParserSources();
        return ResponseEntity.ok(sources);
    }

    /**
     * Запустить парсинг мероприятий из всех настроенных источников.
     *
     * @return Информация о результатах парсинга
     */
    @PostMapping("/parse-all")
    public ResponseEntity<Map<String, Object>> parseAllSources() {
        List<String> sources = eventParsingScheduler.getParserSources();

        if (sources.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Нет настроенных источников для парсинга. Настройте parser.sources в application.properties.");
            return ResponseEntity.badRequest().body(response);
        }

        int parsedCount = eventParsingScheduler.parseEventsFromSources(sources);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("parsedCount", parsedCount);
        response.put("sources", sources);

        return ResponseEntity.ok(response);
    }

    /**
     * Запустить парсинг мероприятий из конкретного источника.
     *
     * @param source URL источника для парсинга
     * @return Информация о результатах парсинга
     */
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseSource(@RequestParam String source) {
        log.info("Запуск ручного парсинга из источника: {}", source);

        Map<String, Object> response = new HashMap<>();

        try {
            CompletableFuture<List<Event>> future;

            // Выбираем подходящий парсер в зависимости от типа источника
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

            List<Event> events = future.get(); // Ждем завершения парсинга

            int savedCount = eventService.saveEvents(events);

            response.put("success", true);
            response.put("source", source);
            response.put("parsedCount", events.size());
            response.put("savedCount", savedCount);

            log.info("Завершен ручной парсинг из источника: {}. Спарсено: {}, Сохранено: {}", 
                    source, events.size(), savedCount);

            return ResponseEntity.ok(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Парсинг прерван: {}", e.getMessage(), e);

            response.put("success", false);
            response.put("error", "Парсинг был прерван: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (ExecutionException e) {
            log.error("Ошибка при парсинге: {}", e.getMessage(), e);

            response.put("success", false);
            response.put("error", "Ошибка при парсинге: " + e.getCause().getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при парсинге: {}", e.getMessage(), e);

            response.put("success", false);
            response.put("error", "Непредвиденная ошибка: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Получить статистику по парсингу мероприятий.
     *
     * @return Статистика парсинга
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getParserStats() {
        List<String> sources = eventParsingScheduler.getParserSources();
        int totalEvents = eventService.getAllEvents().size();
        int eventsWithoutMessages = eventService.getEventsWithoutMessages().size();
        int eventsAwaitingResponse = eventService.getEventsAwaitingResponse().size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("configuredSources", sources);
        stats.put("sourceCount", sources.size());
        stats.put("totalEvents", totalEvents);
        stats.put("eventsWithoutMessages", eventsWithoutMessages);
        stats.put("eventsAwaitingResponse", eventsAwaitingResponse);

        return ResponseEntity.ok(stats);
    }
}
