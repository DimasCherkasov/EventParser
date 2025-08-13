package com.eventparser.telegram;

import com.eventparser.model.Event;
import com.eventparser.repository.EventRepository;
import com.eventparser.scheduler.EventParsingScheduler;
import com.eventparser.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    private final EventService eventService;

    private final EventParsingScheduler eventParsingScheduler;

    private final String webInterfaceUrl;

    // Команды бота
    private static final String COMMAND_START = "/start";
    private static final String COMMAND_HELP = "/help";
    private static final String COMMAND_STATS = "/stats";
    private static final String COMMAND_UPCOMING = "/upcoming";
    private static final String COMMAND_AWAITING = "/awaiting";
    private static final String COMMAND_REFRESH = "/refresh";
    private static final String COMMAND_CLEAR = "/clear";

    // Пункты меню
    private static final String MENU_STATS = "📊 Статистика";
    private static final String MENU_UPCOMING = "📅 Предстоящие мероприятия";
    private static final String MENU_AWAITING = "⏳ Ожидающие ответа";
    private static final String MENU_HELP = "❓ Помощь";
    private static final String MENU_REFRESH = "🔄 Обновить данные";
    private static final String MENU_CLEAR = "🗑️ Очистить базу данных";

    // Конструктор с внедрением зависимостей
    public EventParserBot(
            @Value("${telegram.bot.token}") String botToken, // Получаем токен из application.properties
            @Value("${telegram.bot.username}") String botUsername, // Получаем имя пользователя из application.properties
            @Value("${app.web-interface.url}") String webInterfaceUrl, // Получаем URL веб-интерфейса из application.properties
            EventRepository eventRepository,
            EventService eventService,
            EventParsingScheduler eventParsingScheduler) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.webInterfaceUrl = webInterfaceUrl;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.eventParsingScheduler = eventParsingScheduler;
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

        // Проверяем, является ли сообщение командой
        if (text.startsWith("/")) {
            handleCommand(chatId, text);
            return;
        }

        // Проверяем, является ли сообщение пунктом меню
        if (text.equals(MENU_STATS)) {
            sendStatistics(chatId);
            return;
        } else if (text.equals(MENU_UPCOMING)) {
            sendUpcomingEvents(chatId);
            return;
        } else if (text.equals(MENU_AWAITING)) {
            sendAwaitingEvents(chatId);
            return;
        } else if (text.equals(MENU_HELP)) {
            sendHelpMessage(chatId);
            return;
        } else if (text.equals(MENU_REFRESH)) {
            sendRefreshData(chatId);
            return;
        } else if (text.equals(MENU_CLEAR)) {
            sendClearDatabase(chatId);
            return;
        }

        // Проверяем, связан ли этот чат с каким-либо мероприятием
        if (chatToEventMap.containsKey(chatId)) {
            // Если чат уже связан с мероприятием, обрабатываем ответ
            Long eventId = chatToEventMap.get(chatId);
            handleEventResponse(eventId, text);

            // Отправляем сообщение о том, что ответ получен
            sendTelegramMessage(chatId, "Спасибо за ответ! Ваше сообщение записано.", createMainMenu());
        } else if (username != null && pendingMessages.containsKey(username)) {
            // Это новый разговор с пользователем, которому мы пытались отправить сообщение
            Long eventId = pendingMessages.get(username);
            chatToEventMap.put(chatId, eventId); // Связываем чат с мероприятием
            pendingMessages.remove(username); // Удаляем из списка ожидающих

            // Отправляем приветственное сообщение
            sendTelegramMessage(chatId, "Спасибо за ответ! Ваше сообщение записано.", createMainMenu());

            // Обновляем мероприятие в базе данных
            handleEventResponse(eventId, text);
        } else {
            // Неизвестный пользователь/чат - отправляем приветственное сообщение с меню
            sendWelcomeMessage(chatId);
        }
    }

    /**
     * Handle a command from a user.
     *
     * @param chatId The chat ID
     * @param command The command text
     * 
     * Обрабатывает команду от пользователя.
     *
     * @param chatId ID чата
     * @param command Текст команды
     */
    private void handleCommand(String chatId, String command) {
        switch (command) {
            case COMMAND_START:
                sendWelcomeMessage(chatId);
                break;
            case COMMAND_HELP:
                sendHelpMessage(chatId);
                break;
            case COMMAND_STATS:
                sendStatistics(chatId);
                break;
            case COMMAND_UPCOMING:
                sendUpcomingEvents(chatId);
                break;
            case COMMAND_AWAITING:
                sendAwaitingEvents(chatId);
                break;
            case COMMAND_REFRESH:
                sendRefreshData(chatId);
                break;
            case COMMAND_CLEAR:
                sendClearDatabase(chatId);
                break;
            default:
                sendTelegramMessage(chatId, "Неизвестная команда. Используйте /help для получения списка доступных команд.", createMainMenu());
                break;
        }
    }

    /**
     * Send a welcome message to a user.
     *
     * @param chatId The chat ID
     * 
     * Отправляет приветственное сообщение пользователю.
     *
     * @param chatId ID чата
     */
    private void sendWelcomeMessage(String chatId) {
        StringBuilder message = new StringBuilder();
        message.append("👋 Добро пожаловать в Event Parser Bot!\n\n");
        message.append("Я помогаю управлять информацией о мероприятиях и отслеживать ответы организаторов.\n\n");
        message.append("Используйте меню ниже или команды для взаимодействия со мной:\n");
        message.append("- /stats - Показать статистику мероприятий\n");
        message.append("- /upcoming - Показать предстоящие мероприятия\n");
        message.append("- /awaiting - Показать мероприятия, ожидающие ответа\n");
        message.append("- /refresh - Обновить данные о мероприятиях\n");
        message.append("- /clear - Очистить базу данных мероприятий\n");
        message.append("- /help - Показать справку\n\n");
        message.append("🌐 Веб-интерфейс доступен по адресу: ").append(webInterfaceUrl).append("\n\n");
        message.append("Чем я могу помочь вам сегодня?");

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Send a help message to a user.
     *
     * @param chatId The chat ID
     * 
     * Отправляет справочное сообщение пользователю.
     *
     * @param chatId ID чата
     */
    private void sendHelpMessage(String chatId) {
        StringBuilder message = new StringBuilder();
        message.append("📚 Справка по использованию Event Parser Bot\n\n");
        message.append("Доступные команды:\n");
        message.append("- /start - Начать взаимодействие с ботом\n");
        message.append("- /stats - Показать статистику мероприятий\n");
        message.append("- /upcoming - Показать предстоящие мероприятия\n");
        message.append("- /awaiting - Показать мероприятия, ожидающие ответа\n");
        message.append("- /refresh - Обновить данные о мероприятиях\n");
        message.append("- /clear - Очистить базу данных мероприятий\n");
        message.append("- /help - Показать эту справку\n\n");
        message.append("Вы также можете использовать кнопки меню для быстрого доступа к функциям бота.\n\n");
        message.append("🌐 Веб-интерфейс программы доступен по адресу:\n").append(webInterfaceUrl);

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Send statistics about events to a user.
     *
     * @param chatId The chat ID
     * 
     * Отправляет статистику по мероприятиям пользователю.
     *
     * @param chatId ID чата
     */
    private void sendStatistics(String chatId) {
        int totalEvents = eventService.getAllEvents().size();
        int upcomingEvents = eventService.getUpcomingEvents().size();
        int eventsWithoutMessages = eventService.getEventsWithoutMessages().size();
        int eventsAwaitingResponse = eventService.getEventsAwaitingResponse().size();

        StringBuilder message = new StringBuilder();
        message.append("📊 Статистика мероприятий\n\n");
        message.append("📌 Всего мероприятий: ").append(totalEvents).append("\n");
        message.append("📅 Предстоящих мероприятий: ").append(upcomingEvents).append("\n");
        message.append("📩 Мероприятий без отправленных сообщений: ").append(eventsWithoutMessages).append("\n");
        message.append("⏳ Мероприятий, ожидающих ответа: ").append(eventsAwaitingResponse).append("\n\n");
        message.append("Данные актуальны на: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("\n\n");
        message.append("🌐 Для более подробной статистики посетите веб-интерфейс:\n").append(webInterfaceUrl).append("/parser");

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Send a list of upcoming events to a user.
     *
     * @param chatId The chat ID
     * 
     * Отправляет список предстоящих мероприятий пользователю.
     *
     * @param chatId ID чата
     */
    private void sendUpcomingEvents(String chatId) {
        List<Event> upcomingEvents = eventService.getUpcomingEvents();

        if (upcomingEvents.isEmpty()) {
            sendTelegramMessage(chatId, "Нет предстоящих мероприятий.", createMainMenu());
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("📅 Предстоящие мероприятия (").append(upcomingEvents.size()).append(")\n\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        // Ограничиваем список до 10 мероприятий, чтобы не превысить лимит сообщения
        int count = Math.min(upcomingEvents.size(), 10);
        for (int i = 0; i < count; i++) {
            Event event = upcomingEvents.get(i);
            message.append(i + 1).append(". ").append(event.getName()).append("\n");
            message.append("   📍 ").append(event.getLocation()).append("\n");
            message.append("   🕒 ").append(event.getDate().format(formatter)).append("\n");
            if (event.getPrice() != null) {
                message.append("   💰 ").append(event.getPrice()).append(" руб.\n");
            }
            message.append("\n");
        }

        if (upcomingEvents.size() > 10) {
            message.append("... и еще ").append(upcomingEvents.size() - 10).append(" мероприятий.\n\n");
        } else {
            message.append("\n");
        }

        message.append("🌐 Полный список предстоящих мероприятий доступен в веб-интерфейсе:\n").append(webInterfaceUrl).append("/");

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Send a list of events awaiting response to a user.
     *
     * @param chatId The chat ID
     * 
     * Отправляет список мероприятий, ожидающих ответа, пользователю.
     *
     * @param chatId ID чата
     */
    private void sendAwaitingEvents(String chatId) {
        List<Event> awaitingEvents = eventService.getEventsAwaitingResponse();

        if (awaitingEvents.isEmpty()) {
            sendTelegramMessage(chatId, "Нет мероприятий, ожидающих ответа.", createMainMenu());
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("⏳ Мероприятия, ожидающие ответа (").append(awaitingEvents.size()).append(")\n\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        // Ограничиваем список до 10 мероприятий, чтобы не превысить лимит сообщения
        int count = Math.min(awaitingEvents.size(), 10);
        for (int i = 0; i < count; i++) {
            Event event = awaitingEvents.get(i);
            message.append(i + 1).append(". ").append(event.getName()).append("\n");
            message.append("   📍 ").append(event.getLocation()).append("\n");
            message.append("   🕒 ").append(event.getDate().format(formatter)).append("\n");
            message.append("   ✉️ ").append(event.getOrganizerContact()).append("\n");
            message.append("\n");
        }

        if (awaitingEvents.size() > 10) {
            message.append("... и еще ").append(awaitingEvents.size() - 10).append(" мероприятий.\n\n");
        } else {
            message.append("\n");
        }

        message.append("🌐 Полный список мероприятий, ожидающих ответа, доступен в веб-интерфейсе:\n").append(webInterfaceUrl).append("/events/awaiting-response");

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Refresh data by triggering the parsing process and send a confirmation message.
     *
     * @param chatId The chat ID
     * 
     * Обновляет данные, запуская процесс парсинга, и отправляет подтверждающее сообщение.
     *
     * @param chatId ID чата
     */
    private void sendRefreshData(String chatId) {
        sendTelegramMessage(chatId, "🔄 Запуск обновления данных...", null);

        List<String> sources = eventParsingScheduler.getParserSources();

        if (sources.isEmpty()) {
            sendTelegramMessage(chatId, "❌ Нет настроенных источников для парсинга. Настройте parser.sources в application.properties.", createMainMenu());
            return;
        }

        int parsedCount = eventParsingScheduler.parseEventsFromSources(sources);

        StringBuilder message = new StringBuilder();
        message.append("✅ Обновление данных завершено!\n\n");
        message.append("📊 Результаты:\n");
        message.append("- Обработано источников: ").append(sources.size()).append("\n");
        message.append("- Добавлено новых мероприятий: ").append(parsedCount).append("\n\n");
        message.append("Данные актуальны на: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("\n\n");
        message.append("🌐 Для просмотра обновленных данных посетите веб-интерфейс:\n").append(webInterfaceUrl);

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Clear all events from the database and send a confirmation message.
     *
     * @param chatId The chat ID
     * 
     * Очищает все мероприятия из базы данных и отправляет подтверждающее сообщение.
     *
     * @param chatId ID чата
     */
    private void sendClearDatabase(String chatId) {
        sendTelegramMessage(chatId, "🗑️ Очистка базы данных...", null);

        int deletedCount = eventService.clearAllEvents();

        StringBuilder message = new StringBuilder();
        message.append("✅ База данных успешно очищена!\n\n");
        message.append("📊 Результаты:\n");
        message.append("- Удалено мероприятий: ").append(deletedCount).append("\n\n");
        message.append("Операция выполнена: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("\n\n");
        message.append("🌐 Для проверки посетите веб-интерфейс:\n").append(webInterfaceUrl);

        sendTelegramMessage(chatId, message.toString(), createMainMenu());
    }

    /**
     * Create a keyboard markup with the main menu buttons.
     *
     * @return The keyboard markup
     * 
     * Создает разметку клавиатуры с кнопками главного меню.
     *
     * @return Разметка клавиатуры
     */
    private ReplyKeyboardMarkup createMainMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(MENU_STATS));
        row1.add(new KeyboardButton(MENU_UPCOMING));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(MENU_AWAITING));
        row2.add(new KeyboardButton(MENU_HELP));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton(MENU_REFRESH));
        row3.add(new KeyboardButton(MENU_CLEAR));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
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
     * @param replyKeyboardMarkup The keyboard markup to display (optional)
     * @return true if the message was sent successfully, false otherwise
     * 
     * Отправляет сообщение пользователю Telegram.
     *
     * @param chatId ID чата для отправки сообщения
     * @param text Текст сообщения
     * @param replyKeyboardMarkup Разметка клавиатуры для отображения (опционально)
     * @return true, если сообщение успешно отправлено, false в противном случае
     */
    public boolean sendTelegramMessage(String chatId, String text, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage message = new SendMessage(); // Создаем объект сообщения
        message.setChatId(chatId); // Устанавливаем ID чата
        message.setText(text); // Устанавливаем текст сообщения

        if (replyKeyboardMarkup != null) {
            message.setReplyMarkup(replyKeyboardMarkup); // Устанавливаем разметку клавиатуры, если она предоставлена
        }

        try {
            execute(message); // Отправляем сообщение
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, e.getMessage(), e); // Логируем ошибку
            return false;
        }
    }

    /**
     * Send a message to a Telegram user without a keyboard.
     *
     * @param chatId The chat ID to send the message to
     * @param text The message text
     * @return true if the message was sent successfully, false otherwise
     * 
     * Отправляет сообщение пользователю Telegram без клавиатуры.
     *
     * @param chatId ID чата для отправки сообщения
     * @param text Текст сообщения
     * @return true, если сообщение успешно отправлено, false в противном случае
     */
    public boolean sendTelegramMessage(String chatId, String text) {
        return sendTelegramMessage(chatId, text, null);
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
