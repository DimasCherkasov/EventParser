package com.eventparser.controller;

import com.eventparser.model.Event;
import com.eventparser.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST контроллер для управления мероприятиями.
 * Предоставляет API для выполнения CRUD операций над мероприятиями.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Получить все мероприятия.
     *
     * @return Список всех мероприятий
     */
    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        List<Event> events = eventService.getAllEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятие по ID.
     *
     * @param id ID мероприятия
     * @return Мероприятие, если найдено
     */
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        Optional<Event> event = eventService.getEventById(id);
        return event.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Получить предстоящие мероприятия (в течение следующих 7 дней).
     *
     * @return Список предстоящих мероприятий
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<Event>> getUpcomingEvents() {
        List<Event> events = eventService.getUpcomingEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятия по названию.
     *
     * @param name Название для поиска
     * @return Список мероприятий, содержащих указанное название
     */
    @GetMapping("/search/name")
    public ResponseEntity<List<Event>> getEventsByName(@RequestParam String name) {
        List<Event> events = eventService.getEventsByName(name);
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятия по месту проведения.
     *
     * @param location Место проведения для поиска
     * @return Список мероприятий в указанном месте
     */
    @GetMapping("/search/location")
    public ResponseEntity<List<Event>> getEventsByLocation(@RequestParam String location) {
        List<Event> events = eventService.getEventsByLocation(location);
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятия после указанной даты.
     *
     * @param date Дата, после которой искать мероприятия
     * @return Список мероприятий после указанной даты
     */
    @GetMapping("/search/date")
    public ResponseEntity<List<Event>> getEventsByDateAfter(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date) {
        List<Event> events = eventService.getEventsByDateAfter(date);
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятия по контакту организатора.
     *
     * @param contact Контакт организатора для поиска
     * @return Список мероприятий с указанным контактом организатора
     */
    @GetMapping("/search/organizer")
    public ResponseEntity<List<Event>> getEventsByOrganizerContact(@RequestParam String contact) {
        List<Event> events = eventService.getEventsByOrganizerContact(contact);
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятия, для которых не были отправлены сообщения.
     *
     * @return Список мероприятий без отправленных сообщений
     */
    @GetMapping("/without-messages")
    public ResponseEntity<List<Event>> getEventsWithoutMessages() {
        List<Event> events = eventService.getEventsWithoutMessages();
        return ResponseEntity.ok(events);
    }

    /**
     * Получить мероприятия, ожидающие ответа.
     *
     * @return Список мероприятий, ожидающих ответа
     */
    @GetMapping("/awaiting-response")
    public ResponseEntity<List<Event>> getEventsAwaitingResponse() {
        List<Event> events = eventService.getEventsAwaitingResponse();
        return ResponseEntity.ok(events);
    }

    /**
     * Создать новое мероприятие.
     *
     * @param event Данные мероприятия
     * @return Созданное мероприятие
     */
    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        // Устанавливаем значения по умолчанию для новых мероприятий
        event.setMessageSent(false);
        event.setResponseReceived(false);
        
        List<Event> events = List.of(event);
        int savedCount = eventService.saveEvents(events);
        
        if (savedCount > 0) {
            return ResponseEntity.status(HttpStatus.CREATED).body(event);
        } else {
            // Если мероприятие не было сохранено (возможно, дубликат)
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Обновить существующее мероприятие.
     *
     * @param id ID мероприятия для обновления
     * @param event Новые данные мероприятия
     * @return Обновленное мероприятие
     */
    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(@PathVariable Long id, @RequestBody Event event) {
        Optional<Event> existingEvent = eventService.getEventById(id);
        
        if (existingEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        event.setId(id); // Убедимся, что ID установлен правильно
        Event updatedEvent = eventService.updateEvent(event);
        return ResponseEntity.ok(updatedEvent);
    }

    /**
     * Удалить мероприятие.
     *
     * @param id ID мероприятия для удаления
     * @return Статус операции
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        Optional<Event> existingEvent = eventService.getEventById(id);
        
        if (existingEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Отметить, что сообщение было отправлено для мероприятия.
     *
     * @param id ID мероприятия
     * @return Обновленное мероприятие
     */
    @PutMapping("/{id}/mark-message-sent")
    public ResponseEntity<Event> markMessageSent(@PathVariable Long id) {
        try {
            Event event = eventService.markMessageSent(id);
            return ResponseEntity.ok(event);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Отметить, что получен ответ для мероприятия.
     *
     * @param id ID мероприятия
     * @return Обновленное мероприятие
     */
    @PutMapping("/{id}/mark-response-received")
    public ResponseEntity<Event> markResponseReceived(@PathVariable Long id) {
        try {
            Event event = eventService.markResponseReceived(id);
            return ResponseEntity.ok(event);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}