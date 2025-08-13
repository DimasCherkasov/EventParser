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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSender implements MessageSender {

    private final EventParserBot telegramBot;
    
    private static final Pattern TELEGRAM_PATTERN = Pattern.compile("@[a-zA-Z0-9_]{5,32}");

    @Override
    public boolean sendMessage(Event event, String message) {
        if (!canHandle(event.getOrganizerContact())) {
            log.warn("Cannot send Telegram message to non-Telegram contact: {}", event.getOrganizerContact());
            return false;
        }

        String username = event.getOrganizerContact();
        
        // Queue the message to be sent when the user contacts the bot
        telegramBot.queueMessageForUsername(username, event.getId());
        
        log.info("Queued Telegram message for user {} for event {}", username, event.getId());
        
        // We consider this a success since we've queued the message
        return true;
    }

    @Async
    @Override
    public CompletableFuture<Boolean> sendMessageAsync(Event event, String message) {
        return CompletableFuture.completedFuture(sendMessage(event, message));
    }

    @Override
    public boolean canHandle(String contact) {
        return contact != null && TELEGRAM_PATTERN.matcher(contact).matches();
    }
}