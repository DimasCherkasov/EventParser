package com.eventparser.controller;

import com.eventparser.model.Event;
import com.eventparser.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST контроллер для получения статистики по мероприятиям.
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final EventService eventService;

    /**
     * Получить общую статистику по мероприятиям.
     *
     * @return Общая статистика
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getGeneralStatistics() {
        List<Event> allEvents = eventService.getAllEvents();
        List<Event> upcomingEvents = eventService.getUpcomingEvents();
        List<Event> eventsWithoutMessages = eventService.getEventsWithoutMessages();
        List<Event> eventsAwaitingResponse = eventService.getEventsAwaitingResponse();
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalEvents", allEvents.size());
        statistics.put("upcomingEvents", upcomingEvents.size());
        statistics.put("eventsWithoutMessages", eventsWithoutMessages.size());
        statistics.put("eventsAwaitingResponse", eventsAwaitingResponse.size());
        
        // Добавляем текущее время для отслеживания актуальности данных
        statistics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Получить статистику по местам проведения мероприятий.
     *
     * @return Статистика по местам проведения
     */
    @GetMapping("/locations")
    public ResponseEntity<Map<String, Object>> getLocationStatistics() {
        List<Event> allEvents = eventService.getAllEvents();
        
        // Группируем мероприятия по местам проведения и считаем количество
        Map<String, Long> locationCounts = allEvents.stream()
                .collect(Collectors.groupingBy(Event::getLocation, Collectors.counting()));
        
        // Находим самые популярные места проведения (топ-10)
        List<Map.Entry<String, Long>> topLocations = locationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalLocations", locationCounts.size());
        statistics.put("topLocations", topLocations);
        statistics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Получить статистику по организаторам мероприятий.
     *
     * @return Статистика по организаторам
     */
    @GetMapping("/organizers")
    public ResponseEntity<Map<String, Object>> getOrganizerStatistics() {
        List<Event> allEvents = eventService.getAllEvents();
        
        // Группируем мероприятия по организаторам и считаем количество
        Map<String, Long> organizerCounts = allEvents.stream()
                .filter(event -> event.getOrganizerName() != null && !event.getOrganizerName().isEmpty())
                .collect(Collectors.groupingBy(Event::getOrganizerName, Collectors.counting()));
        
        // Находим самых активных организаторов (топ-10)
        List<Map.Entry<String, Long>> topOrganizers = organizerCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalOrganizers", organizerCounts.size());
        statistics.put("topOrganizers", topOrganizers);
        statistics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Получить статистику по датам проведения мероприятий.
     *
     * @return Статистика по датам
     */
    @GetMapping("/dates")
    public ResponseEntity<Map<String, Object>> getDateStatistics() {
        List<Event> allEvents = eventService.getAllEvents();
        LocalDateTime now = LocalDateTime.now();
        
        // Считаем количество прошедших и предстоящих мероприятий
        long pastEvents = allEvents.stream()
                .filter(event -> event.getDate().isBefore(now))
                .count();
        
        long futureEvents = allEvents.stream()
                .filter(event -> event.getDate().isAfter(now))
                .count();
        
        // Группируем предстоящие мероприятия по месяцам
        Map<String, Long> eventsByMonth = allEvents.stream()
                .filter(event -> event.getDate().isAfter(now))
                .collect(Collectors.groupingBy(
                        event -> event.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("pastEvents", pastEvents);
        statistics.put("futureEvents", futureEvents);
        statistics.put("eventsByMonth", eventsByMonth);
        statistics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Получить статистику по ответам организаторов.
     *
     * @return Статистика по ответам
     */
    @GetMapping("/responses")
    public ResponseEntity<Map<String, Object>> getResponseStatistics() {
        List<Event> allEvents = eventService.getAllEvents();
        
        // Считаем количество мероприятий с отправленными сообщениями и полученными ответами
        long eventsWithMessages = allEvents.stream()
                .filter(Event::isMessageSent)
                .count();
        
        long eventsWithResponses = allEvents.stream()
                .filter(Event::isResponseReceived)
                .count();
        
        // Вычисляем процент ответов
        double responseRate = eventsWithMessages > 0 
                ? (double) eventsWithResponses / eventsWithMessages * 100 
                : 0;
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("eventsWithMessages", eventsWithMessages);
        statistics.put("eventsWithResponses", eventsWithResponses);
        statistics.put("responseRate", String.format("%.2f%%", responseRate));
        statistics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        return ResponseEntity.ok(statistics);
    }
}