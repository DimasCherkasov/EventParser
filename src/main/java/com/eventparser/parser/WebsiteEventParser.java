package com.eventparser.parser;

import com.eventparser.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of EventParser for parsing events from websites using Jsoup.
 * 
 * Реализация интерфейса EventParser для парсинга мероприятий с веб-сайтов с использованием библиотеки Jsoup.
 */
@Slf4j // Аннотация Lombok для автоматического создания логгера
@Component // Аннотация Spring, указывающая, что этот класс является компонентом
public class WebsiteEventParser implements EventParser {

    @Value("${parser.timeout:10000}") // Значение из application.properties с дефолтным значением 10000
    private int timeout; // Таймаут подключения в миллисекундах

    @Value("${parser.user-agent:Mozilla/5.0}") // Значение из application.properties с дефолтным значением
    private String userAgent; // User-Agent для HTTP-запросов

    @Value("${parser.threads:5}") // Значение из application.properties с дефолтным значением 5
    private int threadCount; // Количество потоков для параллельного парсинга

    private final Executor executor; // Исполнитель для асинхронных задач

    // Регулярные выражения для извлечения контактной информации
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"); // Шаблон для email
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9\\s\\-()]{10,20}"); // Шаблон для телефонных номеров
    private static final Pattern TELEGRAM_PATTERN = Pattern.compile("@[a-zA-Z0-9_]{5,32}"); // Шаблон для Telegram-аккаунтов

    public WebsiteEventParser() {
        this.executor = Executors.newFixedThreadPool(threadCount); // Создаем пул потоков для параллельного парсинга
    }

    @Override
    public List<Event> parseEvents(String url) {
        log.info("Parsing events from URL: {}", url); // Логируем начало парсинга
        List<Event> events = new ArrayList<>(); // Список для хранения спарсенных мероприятий

        try {
            // Подключаемся к URL и получаем HTML-документ
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent) // Устанавливаем User-Agent
                    .timeout(timeout) // Устанавливаем таймаут
                    .get(); // Выполняем GET-запрос

            // Это общая реализация, которую нужно настроить для конкретных веб-сайтов
            // Для демонстрации предполагаем общую структуру
            // Выбираем элементы, которые могут представлять мероприятия
            Elements eventElements = doc.select(".event, .event-item, div[itemtype='http://schema.org/Event']");

            // Обрабатываем каждый найденный элемент мероприятия
            for (Element eventElement : eventElements) {
                try {
                    // Парсим элемент в объект Event
                    Event event = parseEventElement(eventElement, url);
                    if (event != null) {
                        events.add(event); // Добавляем в список, если успешно спарсили
                    }
                } catch (Exception e) {
                    log.error("Error parsing event element: {}", e.getMessage(), e); // Логируем ошибку
                }
            }

            log.info("Parsed {} events from URL: {}", events.size(), url); // Логируем результат
        } catch (IOException e) {
            log.error("Error connecting to URL: {}", url, e); // Логируем ошибку подключения
        }

        return events; // Возвращаем список спарсенных мероприятий
    }

    @Override
    public CompletableFuture<List<Event>> parseEventsAsync(String url) {
        // Асинхронный метод парсинга, выполняется в отдельном потоке
        return CompletableFuture.supplyAsync(() -> parseEvents(url), executor);
    }

    /**
     * Parse an individual event element from the HTML.
     *
     * @param element The HTML element representing an event
     * @param sourceUrl The source URL of the page
     * @return The parsed Event object, or null if parsing failed
     * 
     * Парсит отдельный HTML-элемент мероприятия.
     *
     * @param element HTML-элемент, представляющий мероприятие
     * @param sourceUrl URL-адрес источника страницы
     * @return Объект Event с данными мероприятия или null, если парсинг не удался
     */
    private Event parseEventElement(Element element, String sourceUrl) {
        // Извлекаем название мероприятия
        String name = extractText(element, ".event-name, .title, h1, h2, h3");
        if (name == null || name.isEmpty()) {
            log.warn("Skipping event with no name"); // Пропускаем мероприятие без названия
            return null;
        }

        // Извлекаем дату мероприятия
        String dateStr = extractText(element, ".event-date, .date, time");
        LocalDateTime date = parseDate(dateStr); // Парсим строку даты в объект LocalDateTime
        if (date == null) {
            log.warn("Skipping event with invalid date: {}", dateStr); // Пропускаем мероприятие с некорректной датой
            return null;
        }

        // Извлекаем место проведения мероприятия
        String location = extractText(element, ".event-location, .location, .venue");
        if (location == null || location.isEmpty()) {
            log.warn("Skipping event with no location"); // Пропускаем мероприятие без места проведения
            return null;
        }

        // Извлекаем цену мероприятия (если есть)
        String priceStr = extractText(element, ".event-price, .price");
        BigDecimal price = parsePrice(priceStr); // Парсим строку цены в BigDecimal

        // Извлекаем количество участников (если указано)
        String participantsStr = extractText(element, ".event-participants, .participants");
        Integer participantsCount = parseParticipantsCount(participantsStr); // Парсим строку в Integer

        // Извлекаем имя организатора
        String organizerName = extractText(element, ".event-organizer, .organizer");

        // Извлекаем контактную информацию из различных элементов
        String contactInfo = extractText(element, ".event-contact, .contact, .email, .phone");
        if (contactInfo == null || contactInfo.isEmpty()) {
            // Если нет специального элемента для контактов, пытаемся найти контактную информацию в описании или другом тексте
            contactInfo = element.text();
        }

        // Извлекаем контакт организатора (email, телефон, Telegram)
        String organizerContact = extractContactInfo(contactInfo);
        if (organizerContact == null || organizerContact.isEmpty()) {
            log.warn("Skipping event with no organizer contact"); // Пропускаем мероприятие без контакта организатора
            return null;
        }

        // Создаем и возвращаем объект Event с использованием паттерна Builder
        return Event.builder()
                .name(name) // Название мероприятия
                .date(date) // Дата и время
                .location(location) // Место проведения
                .price(price) // Цена (может быть null)
                .participantsCount(participantsCount) // Количество участников (может быть null)
                .organizerName(organizerName) // Имя организатора (может быть null)
                .organizerContact(organizerContact) // Контакт организатора
                .sourceUrl(sourceUrl) // URL источника
                .build();
    }

    /**
     * Extract text from an element using a CSS selector.
     *
     * @param element The element to extract text from
     * @param selector The CSS selector to use
     * @return The extracted text, or null if no matching element was found
     * 
     * Извлекает текст из элемента, используя CSS-селектор.
     *
     * @param element Элемент, из которого нужно извлечь текст
     * @param selector CSS-селектор для поиска
     * @return Извлеченный текст или null, если подходящий элемент не найден
     */
    private String extractText(Element element, String selector) {
        Element selectedElement = element.select(selector).first(); // Находим первый элемент, соответствующий селектору
        return selectedElement != null ? selectedElement.text().trim() : null; // Возвращаем текст элемента или null
    }

    /**
     * Parse a date string into a LocalDateTime.
     *
     * @param dateStr The date string to parse
     * @return The parsed LocalDateTime, or null if parsing failed
     * 
     * Парсит строку даты в объект LocalDateTime.
     *
     * @param dateStr Строка с датой для парсинга
     * @return Объект LocalDateTime или null, если парсинг не удался
     */
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // Пробуем разные форматы даты
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), // Формат: 2023-01-15 14:30
                DateTimeFormatter.ofPattern("yyyy-MM-dd"), // Формат: 2023-01-15
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"), // Формат: 15.01.2023 14:30
                DateTimeFormatter.ofPattern("dd.MM.yyyy"), // Формат: 15.01.2023
                DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm a"), // Формат: Jan 15, 2023, 02:30 PM
                DateTimeFormatter.ofPattern("MMM d, yyyy") // Формат: Jan 15, 2023
        };

        // Перебираем все форматы и пытаемся распарсить дату
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }

        // Если все форматы не подошли, можно попробовать извлечь дату с помощью регулярных выражений
        // Это упрощенный пример, который нужно расширить для реального использования
        return null;
    }

    /**
     * Parse a price string into a BigDecimal.
     *
     * @param priceStr The price string to parse
     * @return The parsed BigDecimal, or null if parsing failed
     * 
     * Парсит строку с ценой в объект BigDecimal.
     *
     * @param priceStr Строка с ценой для парсинга
     * @return Объект BigDecimal или null, если парсинг не удался
     */
    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return null;
        }

        // Удаляем символы валюты и нечисловые символы, кроме десятичной точки
        String cleanPrice = priceStr.replaceAll("[^0-9.]", "");
        try {
            return new BigDecimal(cleanPrice); // Преобразуем строку в BigDecimal
        } catch (NumberFormatException e) {
            log.warn("Could not parse price: {}", priceStr); // Логируем ошибку
            return null;
        }
    }

    /**
     * Parse a participants count string into an Integer.
     *
     * @param participantsStr The participants count string to parse
     * @return The parsed Integer, or null if parsing failed
     * 
     * Парсит строку с количеством участников в Integer.
     *
     * @param participantsStr Строка с количеством участников для парсинга
     * @return Объект Integer или null, если парсинг не удался
     */
    private Integer parseParticipantsCount(String participantsStr) {
        if (participantsStr == null || participantsStr.isEmpty()) {
            return null;
        }

        // Извлекаем только цифры из строки
        String cleanCount = participantsStr.replaceAll("[^0-9]", "");
        try {
            return Integer.parseInt(cleanCount); // Преобразуем строку в Integer
        } catch (NumberFormatException e) {
            log.warn("Could not parse participants count: {}", participantsStr); // Логируем ошибку
            return null;
        }
    }

    /**
     * Extract contact information from a text string.
     *
     * @param text The text to extract contact information from
     * @return The extracted contact information, or null if none was found
     * 
     * Извлекает контактную информацию из текстовой строки.
     *
     * @param text Текст, из которого нужно извлечь контактную информацию
     * @return Извлеченная контактная информация или null, если ничего не найдено
     */
    private String extractContactInfo(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // Пытаемся найти email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        if (emailMatcher.find()) {
            return emailMatcher.group(); // Возвращаем найденный email
        }

        // Пытаемся найти имя пользователя Telegram
        Matcher telegramMatcher = TELEGRAM_PATTERN.matcher(text);
        if (telegramMatcher.find()) {
            return telegramMatcher.group(); // Возвращаем найденное имя пользователя Telegram
        }

        // Пытаемся найти номер телефона
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        if (phoneMatcher.find()) {
            return phoneMatcher.group(); // Возвращаем найденный номер телефона
        }

        return null; // Ничего не найдено
    }
}
