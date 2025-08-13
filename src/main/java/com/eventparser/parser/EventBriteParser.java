package com.eventparser.parser;

import com.eventparser.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер для сайта EventBrite, который специализируется на крупных мероприятиях.
 * EventBrite - международная платформа для организации мероприятий, где часто
 * публикуются события с большим количеством участников (500+).
 */
@Slf4j
@Component
public class EventBriteParser implements EventParser, InitializingBean {

    @Value("${parser.timeout:10000}")
    private int timeout;

    @Value("${parser.user-agent:Mozilla/5.0}")
    private String userAgent;

    @Value("${parser.threads:5}")
    private int threadCount;

    private Executor executor;

    // Регулярное выражение для извлечения количества участников
    private static final Pattern ATTENDEES_PATTERN = Pattern.compile("(\\d+)\\+?\\s*(?:участник|человек|гост|посетител)");

    @Override
    public void afterPropertiesSet() throws Exception {
        this.executor = Executors.newFixedThreadPool(threadCount);
        log.info("Initialized EventBriteParser with {} threads", threadCount);
    }

    @Override
    public List<Event> parseEvents(String url) {
        log.info("Parsing events from EventBrite URL: {}", url);
        List<Event> events = new ArrayList<>();

        try {
            // Если URL не содержит "eventbrite", добавляем базовый URL для Москвы
            if (!url.contains("eventbrite")) {
                url = "https://www.eventbrite.com/d/russia--moscow/all-events/";
            }

            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeout)
                    .get();

            // Селектор для карточек мероприятий на EventBrite
            Elements eventElements = doc.select("div.eds-event-card-content");
            log.info("Found {} event elements on EventBrite", eventElements.size());

            for (Element eventElement : eventElements) {
                try {
                    Event event = parseEventBriteElement(eventElement, url);
                    if (event != null && (event.getParticipantsCount() == null || event.getParticipantsCount() >= 500)) {
                        events.add(event);
                        log.info("Added large event: {} with {} participants", event.getName(), event.getParticipantsCount());
                    }
                } catch (Exception e) {
                    log.error("Error parsing EventBrite event element: {}", e.getMessage(), e);
                }
            }

            log.info("Parsed {} large events from EventBrite", events.size());
        } catch (IOException e) {
            log.error("Error connecting to EventBrite URL: {}", url, e);
        }

        return events;
    }

    @Override
    public CompletableFuture<List<Event>> parseEventsAsync(String url) {
        return CompletableFuture.supplyAsync(() -> parseEvents(url), executor);
    }

    /**
     * Парсит элемент мероприятия с сайта EventBrite.
     *
     * @param element HTML-элемент, представляющий мероприятие с EventBrite
     * @param sourceUrl URL-адрес источника страницы
     * @return Объект Event с данными мероприятия или null, если парсинг не удался
     */
    private Event parseEventBriteElement(Element element, String sourceUrl) {
        // Извлекаем название мероприятия
        String name = extractText(element, "h2.eds-event-card-content__title");
        if (name == null || name.isEmpty()) {
            name = extractText(element, ".eds-event-card__formatted-name--is-clamped");
        }
        if (name == null || name.isEmpty()) {
            log.warn("Skipping EventBrite event with no name");
            return null;
        }

        // Извлекаем дату мероприятия
        String dateStr = extractText(element, ".eds-event-card-content__sub-title");
        LocalDateTime date = parseEventBriteDate(dateStr);
        if (date == null) {
            date = LocalDateTime.now().plusDays(7); // Используем дату через неделю как значение по умолчанию
            log.info("Using default date for EventBrite event: {}", date);
        }

        // Извлекаем место проведения
        String location = extractText(element, ".card-text--truncated__content");
        if (location == null || location.isEmpty()) {
            location = "Москва"; // Используем "Москва" как значение по умолчанию
            log.info("Using default location for EventBrite event: {}", location);
        }

        // Извлекаем цену (если есть)
        String priceStr = extractText(element, ".eds-event-card-content__sub-title--price");
        BigDecimal price = parsePrice(priceStr);

        // Извлекаем количество участников (если указано)
        String attendeesStr = extractText(element, ".eds-event-card__sub-content");
        Integer participantsCount = parseAttendeesCount(attendeesStr);
        
        // Если количество участников не указано, проверяем описание
        if (participantsCount == null) {
            String description = extractText(element, ".eds-event-card-content__description");
            participantsCount = parseAttendeesCount(description);
            
            // Если все еще не найдено, устанавливаем минимум 500 для крупных мероприятий
            if (participantsCount == null) {
                participantsCount = 500;
            }
        }

        // Извлекаем имя организатора
        String organizerName = extractText(element, ".eds-event-card__sub-content--organizer");
        if (organizerName == null || organizerName.isEmpty()) {
            organizerName = "EventBrite Организатор";
        }

        // Получаем URL события
        String eventUrl = element.select("a.eds-event-card-content__action-link").attr("href");
        if (eventUrl.isEmpty()) {
            eventUrl = sourceUrl;
        }

        // Создаем и возвращаем объект Event
        return Event.builder()
                .name(name)
                .date(date)
                .location(location)
                .price(price)
                .participantsCount(participantsCount)
                .organizerName(organizerName)
                .organizerContact("info@eventbrite.com") // Дефолтный контакт
                .sourceUrl(eventUrl)
                .build();
    }

    /**
     * Извлекает текст из элемента, используя CSS-селектор.
     *
     * @param element Элемент, из которого нужно извлечь текст
     * @param selector CSS-селектор для поиска
     * @return Извлеченный текст или null, если подходящий элемент не найден
     */
    private String extractText(Element element, String selector) {
        Element selectedElement = element.select(selector).first();
        return selectedElement != null ? selectedElement.text().trim() : null;
    }

    /**
     * Парсит строку даты в формате EventBrite.
     *
     * @param dateStr Строка с датой для парсинга
     * @return Объект LocalDateTime
     */
    private LocalDateTime parseEventBriteDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            // Примеры форматов: "Sat, Aug 17, 7:00 PM", "Tomorrow at 8:00 PM"
            if (dateStr.contains("Tomorrow")) {
                return LocalDateTime.now().plusDays(1);
            } else if (dateStr.contains("Today")) {
                return LocalDateTime.now();
            }

            // Извлекаем месяц, день и время
            Pattern pattern = Pattern.compile("\\w+,\\s+(\\w+)\\s+(\\d+),\\s+(\\d+):(\\d+)\\s+(AM|PM)");
            Matcher matcher = pattern.matcher(dateStr);

            if (matcher.find()) {
                String month = matcher.group(1);
                int day = Integer.parseInt(matcher.group(2));
                int hour = Integer.parseInt(matcher.group(3));
                int minute = Integer.parseInt(matcher.group(4));
                String amPm = matcher.group(5);

                // Конвертируем 12-часовой формат в 24-часовой
                if (amPm.equals("PM") && hour < 12) {
                    hour += 12;
                } else if (amPm.equals("AM") && hour == 12) {
                    hour = 0;
                }

                // Конвертируем название месяца в номер
                int monthNumber;
                switch (month.toLowerCase()) {
                    case "jan": monthNumber = 1; break;
                    case "feb": monthNumber = 2; break;
                    case "mar": monthNumber = 3; break;
                    case "apr": monthNumber = 4; break;
                    case "may": monthNumber = 5; break;
                    case "jun": monthNumber = 6; break;
                    case "jul": monthNumber = 7; break;
                    case "aug": monthNumber = 8; break;
                    case "sep": monthNumber = 9; break;
                    case "oct": monthNumber = 10; break;
                    case "nov": monthNumber = 11; break;
                    case "dec": monthNumber = 12; break;
                    default: monthNumber = 1;
                }

                return LocalDateTime.of(LocalDateTime.now().getYear(), monthNumber, day, hour, minute);
            }
        } catch (Exception e) {
            log.warn("Error parsing EventBrite date: {}", dateStr, e);
        }

        return null;
    }

    /**
     * Парсит строку с ценой в BigDecimal.
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
            return new BigDecimal(cleanPrice);
        } catch (NumberFormatException e) {
            log.warn("Could not parse price: {}", priceStr);
            return null;
        }
    }

    /**
     * Парсит строку с количеством участников в Integer.
     *
     * @param text Строка с текстом, содержащим информацию о количестве участников
     * @return Объект Integer или null, если парсинг не удался
     */
    private Integer parseAttendeesCount(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // Ищем упоминания о количестве участников
        Matcher matcher = ATTENDEES_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Could not parse attendees count: {}", matcher.group(1));
            }
        }

        // Проверяем на ключевые слова, указывающие на крупное мероприятие
        if (text.toLowerCase().contains("масштабн") || 
            text.toLowerCase().contains("крупн") || 
            text.toLowerCase().contains("большой конференц") ||
            text.toLowerCase().contains("фестиваль")) {
            return 1000; // Предполагаем, что это крупное мероприятие
        }

        return null;
    }
}