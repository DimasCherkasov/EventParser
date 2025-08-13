package com.eventparser.controller;

import com.eventparser.model.Event;
import com.eventparser.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Контроллер для веб-интерфейса приложения.
 * Предоставляет страницы для просмотра мероприятий.
 */
@Controller
@RequiredArgsConstructor
public class WebController {

    private final EventService eventService;

    /**
     * Главная страница со списком мероприятий.
     *
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/")
    public String home(Model model) {
        List<Event> upcomingEvents = eventService.getUpcomingEvents();
        model.addAttribute("events", upcomingEvents);
        model.addAttribute("title", "Предстоящие мероприятия");
        model.addAttribute("content", "events");
        return "th_layout";
    }

    /**
     * Страница с детальной информацией о мероприятии.
     *
     * @param id ID мероприятия
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/events/{id}")
    public String eventDetails(@PathVariable Long id, Model model) {
        Optional<Event> eventOpt = eventService.getEventById(id);

        if (eventOpt.isEmpty()) {
            return "redirect:/";
        }

        model.addAttribute("event", eventOpt.get());
        model.addAttribute("content", "event-details");
        return "th_layout";
    }

    /**
     * Страница со всеми мероприятиями.
     *
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/events")
    public String allEvents(Model model) {
        List<Event> allEvents = eventService.getAllEvents();
        model.addAttribute("events", allEvents);
        model.addAttribute("title", "Все мероприятия");
        model.addAttribute("content", "events");
        return "th_layout";
    }

    /**
     * Страница с мероприятиями, отфильтрованными по названию.
     *
     * @param name Название для поиска
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/events/search")
    public String searchEvents(@RequestParam(required = false) String name,
                              @RequestParam(required = false) String location,
                              Model model) {
        List<Event> events;
        String title;

        if (name != null && !name.isEmpty()) {
            events = eventService.getEventsByName(name);
            title = "Поиск по названию: " + name;
        } else if (location != null && !location.isEmpty()) {
            events = eventService.getEventsByLocation(location);
            title = "Поиск по месту: " + location;
        } else {
            events = eventService.getUpcomingEvents();
            title = "Предстоящие мероприятия";
        }

        model.addAttribute("events", events);
        model.addAttribute("title", title);
        model.addAttribute("content", "events");
        return "th_layout";
    }

    /**
     * Страница с мероприятиями, для которых не были отправлены сообщения.
     *
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/events/without-messages")
    public String eventsWithoutMessages(Model model) {
        List<Event> events = eventService.getEventsWithoutMessages();
        model.addAttribute("events", events);
        model.addAttribute("title", "Мероприятия без отправленных сообщений");
        model.addAttribute("content", "events");
        return "th_layout";
    }

    /**
     * Страница с мероприятиями, ожидающими ответа.
     *
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/events/awaiting-response")
    public String eventsAwaitingResponse(Model model) {
        List<Event> events = eventService.getEventsAwaitingResponse();
        model.addAttribute("events", events);
        model.addAttribute("title", "Мероприятия, ожидающие ответа");
        model.addAttribute("content", "events");
        return "th_layout";
    }

    /**
     * Страница с информацией о парсере.
     *
     * @param model Модель для передачи данных в представление
     * @return Имя шаблона для отображения
     */
    @GetMapping("/parser")
    public String parserInfo(Model model) {
        int totalEvents = eventService.getAllEvents().size();
        int upcomingEvents = eventService.getUpcomingEvents().size();
        int eventsWithoutMessages = eventService.getEventsWithoutMessages().size();
        int eventsAwaitingResponse = eventService.getEventsAwaitingResponse().size();

        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("upcomingEvents", upcomingEvents);
        model.addAttribute("eventsWithoutMessages", eventsWithoutMessages);
        model.addAttribute("eventsAwaitingResponse", eventsAwaitingResponse);
        model.addAttribute("lastUpdate", LocalDateTime.now());
        model.addAttribute("content", "parser");

        return "th_layout";
    }
}
