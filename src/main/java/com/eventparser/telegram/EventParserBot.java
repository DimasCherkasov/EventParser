package com.eventparser.telegram;

import com.eventparser.model.Event;
import com.eventparser.repository.EventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram bot for sending messages to event organizers and tracking responses.
 * 
 * Telegram-бот для отправки сообщений организаторам мероприятий и отслеживания ответов.
 */
@Slf4j // Аннотация Lombok для автоматического создания логгера
@Component // Аннотация Spring, указывающая, что этот класс является компонентом
public class EventParserBot extends TelegramLongPollingBot { // Наследуемся от TelegramLongPollingBot для создания бота

    private final String botUsername; // Имя пользователя бота
    private final String botToken; // Токен бота, полученный от BotFather
    private final EventRepository eventRepository; // Репозиторий для работы с мероприятиями

    // Карта для отслеживания, какой чат связан с каким мероприятием
    private final Map<String, Long> chatToEventMap = new ConcurrentHashMap<>();

    // Карта для хранения отложенных сообщений, которые еще не были отправлены (имя пользователя -> ID мероприятия)
    private final Map<String, Long> pendingMessages = new ConcurrentHashMap<>();

    // Конструктор с внедрением зависимостей
    public EventParserBot(
            @Value("${telegram.bot.token}") String botToken, // Получаем токен из application.properties
            @Value("${telegram.bot.username}") String botUsername, // Получаем имя пользователя из application.properties
            EventRepository eventRepository) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.eventRepository = eventRepository;
    }

    @Override
    public String getBotUsername() {
        // Возвращает имя пользователя бота
        return botUsername;
    }

    @Override
    public String getBotToken() {
        // Возвращает токен бота
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Метод вызывается при получении обновления от Telegram API
        if (update.hasMessage() && update.getMessage().hasText()) {
            // Если обновление содержит сообщение с текстом, обрабатываем его
            processIncomingMessage(update.getMessage());
        }
    }

    /**
     * Process an incoming message from a user.
     *
     * @param message The message received
     * 
     * Обрабатывает входящее сообщение от пользователя.
     *
     * @param message Полученное сообщение
     */
    private void processIncomingMessage(Message message) {
        String chatId = message.getChatId().toString(); // Получаем ID чата
        String username = message.getFrom().getUserName(); // Получаем имя пользователя
        String text = message.getText(); // Получаем текст сообщения

        log.info("Received message from {}: {}", username, text); // Логируем полученное сообщение

        // Проверяем, связан ли этот чат с каким-либо мероприятием
        if (chatToEventMap.containsKey(chatId)) {
            // Если чат уже связан с мероприятием, обрабатываем ответ
            Long eventId = chatToEventMap.get(chatId);
            handleEventResponse(eventId, text);
        } else if (username != null && pendingMessages.containsKey(username)) {
            // Это новый разговор с пользователем, которому мы пытались отправить сообщение
            Long eventId = pendingMessages.get(username);
            chatToEventMap.put(chatId, eventId); // Связываем чат с мероприятием
            pendingMessages.remove(username); // Удаляем из списка ожидающих

            // Отправляем приветственное сообщение
            sendTelegramMessage(chatId, "Thank you for responding! Your message has been recorded.");

            // Обновляем мероприятие в базе данных
            handleEventResponse(eventId, text);
        } else {
            // Неизвестный пользователь/чат
            sendTelegramMessage(chatId, "Hello! I'm the Event Parser Bot. I help manage event information.");
        }
    }

    /**
     * Handle a response for an event.
     *
     * @param eventId The ID of the event
     * @param responseText The response text
     * 
     * Обрабатывает ответ для мероприятия.
     *
     * @param eventId ID мероприятия
     * @param responseText Текст ответа
     */
    private void handleEventResponse(Long eventId, String responseText) {
        Optional<Event> eventOpt = eventRepository.findById(eventId); // Ищем мероприятие по ID
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            event.setResponseReceived(true); // Отмечаем, что получен ответ
            eventRepository.save(event); // Сохраняем изменения в базе данных
            log.info("Received response for event {}: {}", eventId, responseText); // Логируем ответ
        } else {
            log.warn("Received response for unknown event ID: {}", eventId); // Логируем ошибку
        }
    }

    /**
     * Send a message to a Telegram user.
     *
     * @param chatId The chat ID to send the message to
     * @param text The message text
     * @return true if the message was sent successfully, false otherwise
     * 
     * Отправляет сообщение пользователю Telegram.
     *
     * @param chatId ID чата для отправки сообщения
     * @param text Текст сообщения
     * @return true, если сообщение успешно отправлено, false в противном случае
     */
    public boolean sendTelegramMessage(String chatId, String text) {
        SendMessage message = new SendMessage(); // Создаем объект сообщения
        message.setChatId(chatId); // Устанавливаем ID чата
        message.setText(text); // Устанавливаем текст сообщения

        try {
            execute(message); // Отправляем сообщение
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, e.getMessage(), e); // Логируем ошибку
            return false;
        }
    }

    /**
     * Queue a message to be sent to a Telegram username.
     * The message will be sent when the user initiates a conversation with the bot.
     *
     * @param username The Telegram username
     * @param eventId The ID of the event
     * 
     * Ставит сообщение в очередь для отправки пользователю Telegram.
     * Сообщение будет отправлено, когда пользователь инициирует разговор с ботом.
     *
     * @param username Имя пользователя Telegram
     * @param eventId ID мероприятия
     */
    public void queueMessageForUsername(String username, Long eventId) {
        if (username != null && username.startsWith("@")) {
            // Удаляем символ @ в начале, если он присутствует
            username = username.substring(1);
        }

        pendingMessages.put(username, eventId); // Добавляем в карту ожидающих сообщений
        log.info("Queued message for Telegram user @{} for event {}", username, eventId); // Логируем
    }
}
