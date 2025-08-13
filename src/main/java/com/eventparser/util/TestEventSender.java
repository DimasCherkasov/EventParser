package com.eventparser.util;

import com.eventparser.model.Event;
import com.eventparser.service.EventService;
import com.eventparser.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Утилита для добавления тестового события и отправки уведомлений.
 * Запускается автоматически при старте приложения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestEventSender implements CommandLineRunner {

    private final EventService eventService;
    private final MessageService messageService;

    @Override
    public void run(String... args) {
        log.info("Создание тестового события и отправка уведомлений...");
        
        // Создаем тестовое событие для email
        Event emailEvent = Event.builder()
                .name("Тестовое событие для Email")
                .date(LocalDateTime.now().plusDays(1))
                .location("Москва, Тестовая улица, 123")
                .organizerName("Тестовый организатор")
                .organizerContact("event.parser.mega@gmail.com") // Email получателя
                .sourceUrl("https://example.com/test-event")
                .build();
        
        // Создаем тестовое событие для Telegram
        Event telegramEvent = Event.builder()
                .name("Тестовое событие для Telegram")
                .date(LocalDateTime.now().plusDays(2))
                .location("Санкт-Петербург, Тестовый проспект, 456")
                .organizerName("Тестовый организатор")
                .organizerContact("@eeevent_parser_bot") // Telegram бот
                .sourceUrl("https://example.com/test-event-telegram")
                .build();
        
        // Сохраняем события в базу данных
        eventService.saveEvents(Arrays.asList(emailEvent, telegramEvent));
        
        // Отправляем сообщения
        String message = "Это тестовое сообщение от приложения EventParser. " +
                "Если вы видите это сообщение, значит приложение работает корректно!";
        
        // Отправляем сообщения для всех событий без отправленных сообщений
        int sentCount = messageService.sendMessagesToEventsWithoutMessages(message);
        
        log.info("Отправлено {} сообщений", sentCount);
        log.info("Проверьте вашу почту и Telegram для подтверждения получения сообщений");
        log.info("Логи приложения находятся в файле: logs/eventparser.log");
    }
}