package com.eventparser.service;

import com.eventparser.model.Event;
import com.eventparser.telegram.EventParserBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Implementation of MessageSender for sending messages via Telegram.
 * 
 * Реализация интерфейса MessageSender для отправки сообщений через Telegram.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSender implements MessageSender {

    private final EventParserBot telegramBot; // Экземпляр Telegram-бота для отправки сообщений

    // Регулярное выражение для проверки Telegram-имени пользователя (начинается с @ и содержит 5-32 символа)
    private static final Pattern TELEGRAM_PATTERN = Pattern.compile("@[a-zA-Z0-9_]{5,32}");

    @Override
    public boolean sendMessage(Event event, String message) {
        // Проверяем, можем ли мы обработать этот контакт (является ли он Telegram-именем пользователя)
        if (!canHandle(event.getOrganizerContact())) {
            log.warn("Cannot send Telegram message to non-Telegram contact: {}", event.getOrganizerContact());
            return false;
        }

        // Получаем имя пользователя Telegram из контакта организатора
        String username = event.getOrganizerContact();

        // Ставим сообщение в очередь для отправки, когда пользователь свяжется с ботом
        // (в Telegram бот не может первым начать диалог с пользователем)
        telegramBot.queueMessageForUsername(username, event.getId());

        // Логируем информацию о постановке сообщения в очередь
        log.info("Queued Telegram message for user {} for event {}", username, event.getId());

        // Считаем операцию успешной, так как сообщение поставлено в очередь
        return true;
    }

    @Async // Аннотация для асинхронного выполнения метода
    @Override
    public CompletableFuture<Boolean> sendMessageAsync(Event event, String message) {
        // Асинхронная отправка сообщения (в текущей реализации просто вызывает синхронный метод)
        return CompletableFuture.completedFuture(sendMessage(event, message));
    }

    @Override
    public boolean canHandle(String contact) {
        // Проверяем, является ли контакт Telegram-именем пользователя с помощью регулярного выражения
        // (должен начинаться с @ и содержать от 5 до 32 символов)
        return contact != null && TELEGRAM_PATTERN.matcher(contact).matches();
    }
}
