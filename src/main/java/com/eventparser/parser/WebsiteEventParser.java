package com.eventparser.parser;

import com.eventparser.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
public class WebsiteEventParser implements EventParser, InitializingBean {

    @Value("${parser.timeout:10000}") // Значение из application.properties с дефолтным значением 10000
    private int timeout; // Таймаут подключения в миллисекундах

    @Value("${parser.user-agent:Mozilla/5.0}") // Значение из application.properties с дефолтным значением
    private String userAgent; // User-Agent для HTTP-запросов

    // Массив различных User-Agent для ротации
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.107 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59"
    };

    @Value("${parser.threads:5}") // Значение из application.properties с дефолтным значением 5
    private int threadCount; // Количество потоков для параллельного парсинга

    private Executor executor; // Исполнитель для асинхронных задач

    // Регулярные выражения для извлечения контактной информации
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"); // Шаблон для email
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9\\s\\-()]{10,20}"); // Шаблон для телефонных номеров
    private static final Pattern TELEGRAM_PATTERN = Pattern.compile("@[a-zA-Z0-9_]{5,32}"); // Шаблон для Telegram-аккаунтов

    // Конструктор без инициализации executor
    public WebsiteEventParser() {
        // Пустой конструктор
    }

    // Метод, который будет вызван после инициализации всех свойств (реализация интерфейса InitializingBean)
    @Override
    public void afterPropertiesSet() throws Exception {
        this.executor = Executors.newFixedThreadPool(threadCount); // Создаем пул потоков для параллельного парсинга
        log.info("Initialized thread pool with {} threads", threadCount);
    }

    @Override
    public List<Event> parseEvents(String url) {
        log.info("Parsing events from URL: {}", url); // Логируем начало парсинга
        List<Event> events = new ArrayList<>(); // Список для хранения спарсенных мероприятий

        try {
            // Выбираем случайный User-Agent из массива для ротации
            String randomUserAgent = USER_AGENTS[new Random().nextInt(USER_AGENTS.length)];
            log.debug("Using User-Agent: {}", randomUserAgent);

            // Добавляем случайную задержку от 1 до 3 секунд для уменьшения вероятности блокировки
            try {
                int delay = 1000 + new Random().nextInt(2000);
                log.debug("Adding delay of {} ms before request", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sleep interrupted", e);
            }

            // Подключаемся к URL и получаем HTML-документ
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUserAgent) // Устанавливаем случайный User-Agent
                    .timeout(timeout) // Устанавливаем таймаут
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7") // Добавляем заголовок языка
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8") // Добавляем заголовок Accept
                    .header("Connection", "keep-alive") // Добавляем заголовок Connection
                    .header("Cache-Control", "max-age=0") // Добавляем заголовок Cache-Control
                    .get(); // Выполняем GET-запрос

            // Определяем селекторы в зависимости от URL
            Elements eventElements;

            if (url.contains("afisha.yandex.ru")) {
                // Специфичные селекторы для Яндекс Афиши
                log.info("Parsing Yandex Afisha website");
                eventElements = doc.select("div[data-testid='event-card'], div.event-card, div.afisha-event-item, div.event-list-item, div.event-item, article.event");
                log.debug("HTML sample: {}", doc.html().substring(0, Math.min(500, doc.html().length())));
                log.info("Found {} event elements on Yandex Afisha", eventElements.size());

                // Если не найдено элементов, пробуем более общие селекторы
                if (eventElements.isEmpty()) {
                    log.info("Trying more generic selectors for Yandex Afisha");
                    eventElements = doc.select("div.card, div[class*='event'], div[class*='card'], article, div.item");
                    log.info("Found {} event elements with generic selectors", eventElements.size());
                }
            } else if (url.contains("timepad.ru")) {
                // Специфичные селекторы для TimepadRu
                log.info("Parsing TimepadRu website");
                eventElements = doc.select(".event-card, .event-list__item, .event-card__title, .event-card__link, .event-list-item, div[class*='event'], article.event-card");
                log.debug("HTML sample: {}", doc.html().substring(0, Math.min(500, doc.html().length())));
                log.info("Found {} event elements on TimepadRu", eventElements.size());

                // Если не найдено элементов, пробуем более общие селекторы
                if (eventElements.isEmpty()) {
                    log.info("Trying more generic selectors for TimepadRu");
                    eventElements = doc.select("div.card, div[class*='event'], div[class*='card'], article, div.item");
                    log.info("Found {} event elements with generic selectors", eventElements.size());
                }
            } else if (url.contains("kudago.com")) {
                // Специфичные селекторы для KudaGo.com
                log.info("Parsing KudaGo.com website");
                eventElements = doc.select(".event-card, .event-list-item, .post-wrapper, .event-item, .feed-event, div[class*='event'], article.event");
                log.debug("HTML sample: {}", doc.html().substring(0, Math.min(500, doc.html().length())));
                log.info("Found {} event elements on KudaGo.com", eventElements.size());

                // Если не найдено элементов, пробуем более общие селекторы
                if (eventElements.isEmpty()) {
                    log.info("Trying more generic selectors for KudaGo.com");
                    eventElements = doc.select("div.card, div[class*='event'], div[class*='card'], article, div.item, div.feed-item");
                    log.info("Found {} event elements with generic selectors", eventElements.size());
                }

                // Проверяем наличие пагинации на KudaGo.com
                Elements paginationLinks = doc.select(".pagination a");
                if (!paginationLinks.isEmpty()) {
                    log.info("Found pagination on KudaGo.com with {} links", paginationLinks.size());

                    // Парсим события с текущей страницы
                    parseKudaGoEventElements(eventElements, url, events);

                    // Парсим события с дополнительных страниц (до 5 страниц)
                    int pagesParsed = 1; // Уже спарсили первую страницу
                    int maxPages = 5; // Максимальное количество страниц для парсинга

                    for (Element paginationLink : paginationLinks) {
                        if (pagesParsed >= maxPages) {
                            break; // Ограничиваем количество страниц
                        }

                        String pageUrl = paginationLink.attr("href");
                        if (pageUrl != null && !pageUrl.isEmpty()) {
                            // Если URL относительный, добавляем базовый URL
                            if (pageUrl.startsWith("/")) {
                                pageUrl = "https://kudago.com" + pageUrl;
                            } else if (!pageUrl.startsWith("http")) {
                                pageUrl = "https://kudago.com/" + pageUrl;
                            }

                            // Проверяем, что это не текущая страница и не предыдущая
                            if (!pageUrl.equals(url) && !events.isEmpty()) {
                                log.info("Parsing additional page: {}", pageUrl);

                                try {
                                    // Подключаемся к URL дополнительной страницы
                                    Document pageDoc = Jsoup.connect(pageUrl)
                                            .userAgent(userAgent)
                                            .timeout(timeout)
                                            .get();

                                    // Получаем элементы мероприятий с дополнительной страницы
                                    Elements pageEventElements = pageDoc.select(".event-card, .event-list-item, .post-wrapper");
                                    log.info("Found {} event elements on additional page", pageEventElements.size());

                                    // Парсим события с дополнительной страницы
                                    parseKudaGoEventElements(pageEventElements, pageUrl, events);

                                    pagesParsed++;
                                } catch (IOException e) {
                                    log.error("Error connecting to additional page: {}", pageUrl, e);
                                }
                            }
                        }
                    }

                    log.info("Parsed a total of {} events from {} pages of KudaGo.com", events.size(), pagesParsed);
                    return events; // Возвращаем список спарсенных мероприятий со всех страниц
                }
            } else {
                // Общие селекторы для других сайтов
                eventElements = doc.select(".event, .event-item, div[itemtype='http://schema.org/Event']");
            }

            // Обрабатываем каждый найденный элемент мероприятия
            for (Element eventElement : eventElements) {
                try {
                    // Парсим элемент в объект Event
                    Event event;
                    if (url.contains("afisha.yandex.ru")) {
                        event = parseYandexAfishaEventElement(eventElement, url);
                    } else if (url.contains("timepad.ru")) {
                        event = parseTimepadRuEventElement(eventElement, url);
                    } else if (url.contains("kudago.com")) {
                        event = parseKudaGoEventElement(eventElement, url);
                    } else {
                        event = parseEventElement(eventElement, url);
                    }

                    if (event != null) {
                        events.add(event); // Добавляем в список, если успешно спарсили
                    }
                } catch (Exception e) {
                    log.error("Error parsing event element: {}", e.getMessage(), e); // Логируем ошибку
                }
            }

            log.info("Parsed {} events from URL: {}", events.size(), url); // Логируем результат

            // Если не найдено ни одного мероприятия, логируем предупреждение с дополнительной информацией
            if (events.isEmpty()) {
                log.warn("No events found for URL: {}. This might indicate a problem with selectors or anti-scraping protection.", url);
                // Логируем часть HTML для анализа
                log.debug("HTML sample for analysis: {}", doc.html().substring(0, Math.min(1000, doc.html().length())));
                // Логируем заголовки ответа
                log.debug("Response headers: {}", doc.connection().response().headers());
            }
        } catch (IOException e) {
            log.error("Error connecting to URL: {}. Error message: {}", url, e.getMessage(), e); // Логируем ошибку подключения с сообщением
            // Добавляем рекомендации по устранению проблемы
            log.info("This might be due to network issues, site being down, or anti-scraping protection. Try changing User-Agent or using a proxy.");
        } catch (Exception e) {
            log.error("Unexpected error while parsing URL: {}. Error message: {}", url, e.getMessage(), e); // Логируем неожиданные ошибки
        }

        return events; // Возвращаем список спарсенных мероприятий
    }

    /**
     * Parse KudaGo.com event elements and add them to the events list.
     *
     * @param eventElements The event elements to parse
     * @param sourceUrl The source URL of the page
     * @param events The list to add parsed events to
     * 
     * Парсит элементы мероприятий с KudaGo.com и добавляет их в список мероприятий.
     *
     * @param eventElements Элементы мероприятий для парсинга
     * @param sourceUrl URL-адрес источника страницы
     * @param events Список для добавления спарсенных мероприятий
     */
    private void parseKudaGoEventElements(Elements eventElements, String sourceUrl, List<Event> events) {
        for (Element eventElement : eventElements) {
            try {
                Event event = parseKudaGoEventElement(eventElement, sourceUrl);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                log.error("Error parsing KudaGo.com event element: {}", e.getMessage(), e);
            }
        }
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
            // Пробуем извлечь из URL или любого текста
            name = extractText(element, "a");
            if (name == null || name.isEmpty()) {
                name = element.text();
                if (name != null && name.length() > 50) {
                    name = name.substring(0, 50) + "...";
                }
            }

            if (name == null || name.isEmpty()) {
                log.warn("Skipping event with no name"); // Пропускаем мероприятие без названия
                return null;
            }
            log.info("Using extracted text as event name: {}", name);
        }

        // Извлекаем дату мероприятия
        String dateStr = extractText(element, ".event-date, .date, time");
        LocalDateTime date = parseDate(dateStr); // Парсим строку даты в объект LocalDateTime
        if (date == null) {
            // Используем текущую дату + 1 день как значение по умолчанию
            date = LocalDateTime.now().plusDays(1);
            log.info("Using default date for event: {}", date);
        }

        // Извлекаем место проведения мероприятия
        String location = extractText(element, ".event-location, .location, .venue");
        if (location == null || location.isEmpty()) {
            // Используем "Москва" как значение по умолчанию
            location = "Москва";
            log.info("Using default location for event: {}", location);
        }

        // Извлекаем цену мероприятия (если есть)
        String priceStr = extractText(element, ".event-price, .price");
        BigDecimal price = parsePrice(priceStr); // Парсим строку цены в BigDecimal

        // Извлекаем количество участников (если указано)
        String participantsStr = extractText(element, ".event-participants, .participants");
        Integer participantsCount = parseParticipantsCount(participantsStr); // Парсим строку в Integer

        // Извлекаем имя организатора
        String organizerName = extractText(element, ".event-organizer, .organizer");
        if (organizerName == null || organizerName.isEmpty()) {
            organizerName = "Неизвестный организатор";
        }

        // Извлекаем контактную информацию из различных элементов
        String contactInfo = extractText(element, ".event-contact, .contact, .email, .phone");
        if (contactInfo == null || contactInfo.isEmpty()) {
            // Если нет специального элемента для контактов, пытаемся найти контактную информацию в описании или другом тексте
            contactInfo = element.text();
        }

        // Извлекаем контакт организатора (email, телефон, Telegram)
        String organizerContact = extractContactInfo(contactInfo);
        if (organizerContact == null || organizerContact.isEmpty()) {
            // Используем дефолтный контакт
            if (sourceUrl.contains("yandex")) {
                organizerContact = "info@yandex.ru";
            } else if (sourceUrl.contains("timepad")) {
                organizerContact = "info@timepad.ru";
            } else if (sourceUrl.contains("kudago")) {
                organizerContact = "info@kudago.com";
            } else {
                organizerContact = "info@example.com";
            }
            log.info("Using default organizer contact for event: {}", organizerContact);
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
     * Parse an event element from Yandex Afisha website.
     *
     * @param element The HTML element representing an event from Yandex Afisha
     * @param sourceUrl The source URL of the page
     * @return The parsed Event object, or null if parsing failed
     * 
     * Парсит элемент мероприятия с сайта Яндекс Афиша.
     *
     * @param element HTML-элемент, представляющий мероприятие с Яндекс Афиши
     * @param sourceUrl URL-адрес источника страницы
     * @return Объект Event с данными мероприятия или null, если парсинг не удался
     */
    private Event parseYandexAfishaEventElement(Element element, String sourceUrl) {
        log.debug("Parsing Yandex Afisha event element: {}", element.outerHtml().substring(0, Math.min(100, element.outerHtml().length())));

        // Извлекаем название мероприятия
        String name = extractText(element, "h1, h2, h3");
        if (name == null || name.isEmpty()) {
            name = extractText(element, "div[data-component='EventCard'] a");
        }
        if (name == null || name.isEmpty()) {
            // Пробуем извлечь из URL
            String eventUrl = element.select("a").attr("href");
            if (eventUrl != null && !eventUrl.isEmpty()) {
                // Извлекаем название из URL (часть после последнего /)
                String[] parts = eventUrl.split("/");
                if (parts.length > 0) {
                    String lastPart = parts[parts.length - 1];
                    // Удаляем параметры запроса
                    if (lastPart.contains("?")) {
                        lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                    }
                    // Заменяем дефисы на пробелы и делаем первую букву заглавной
                    name = lastPart.replace("-", " ");
                    if (name.length() > 0) {
                        name = name.substring(0, 1).toUpperCase() + name.substring(1);
                    }
                }
            }

            if (name == null || name.isEmpty()) {
                log.warn("Skipping Yandex Afisha event with no name");
                return null;
            }
        }

        // Извлекаем дату мероприятия
        String dateStr = extractText(element, "time");
        if (dateStr == null || dateStr.isEmpty()) {
            dateStr = extractText(element, "div[data-test-id='eventDate']");
        }
        if (dateStr == null || dateStr.isEmpty()) {
            dateStr = extractText(element, "span:contains(сегодня), span:contains(завтра)");
        }

        LocalDateTime date = null;

        if (dateStr != null && !dateStr.isEmpty()) {
            // Пытаемся парсить дату в формате Яндекс Афиши
            try {
                // Предполагаем, что дата в формате "день месяц, время" (например, "15 июня, 19:00")
                // Добавляем текущий год, так как он может отсутствовать
                String fullDateStr = dateStr + " " + LocalDateTime.now().getYear();
                date = parseYandexAfishaDate(fullDateStr);
            } catch (Exception e) {
                log.warn("Error parsing Yandex Afisha date: {}", dateStr, e);
            }
        }

        if (date == null) {
            // Если не удалось распарсить дату, используем текущую дату + 1 день
            date = LocalDateTime.now().plusDays(1);
            log.warn("Using default date for Yandex Afisha event: {}", date);
        }

        // Извлекаем место проведения
        String location = extractText(element, "div[data-test-id='eventLocation']");
        if (location == null || location.isEmpty()) {
            location = extractText(element, "span:contains(Москва)");
        }
        if (location == null || location.isEmpty()) {
            // Пробуем найти адрес в любом тексте
            Elements allText = element.select("*");
            for (Element e : allText) {
                String text = e.text();
                if (text.contains("Москва") || text.contains("ул.") || text.contains("пр-т")) {
                    location = text;
                    break;
                }
            }
        }
        if (location == null || location.isEmpty()) {
            location = "Москва"; // Используем дефолтное значение, так как мы парсим события в Москве
        }

        // Извлекаем цену (если есть)
        String priceStr = extractText(element, "div[data-test-id='eventPrice']");
        if (priceStr == null || priceStr.isEmpty()) {
            priceStr = extractText(element, "span:contains(₽), span:contains(руб)");
        }
        if (priceStr == null || priceStr.isEmpty()) {
            // Пробуем найти цену в любом тексте
            Elements allText = element.select("*");
            for (Element e : allText) {
                String text = e.text();
                if (text.contains("₽") || text.contains("руб") || text.contains("от ")) {
                    priceStr = text;
                    break;
                }
            }
        }
        BigDecimal price = parsePrice(priceStr);

        // Для Яндекс Афиши часто нет информации о количестве участников
        Integer participantsCount = null;

        // Извлекаем имя организатора (если есть)
        String organizerName = extractText(element, "div[data-test-id='eventOrganizer']");

        // Для контактов организатора используем дефолтное значение, так как на странице списка событий
        // обычно нет контактной информации
        String organizerContact = "info@yandex.ru"; // Дефолтный контакт

        // Получаем URL события для дополнительной информации
        String eventUrl = element.select("a").attr("href");
        if (eventUrl != null && !eventUrl.isEmpty() && !eventUrl.startsWith("http")) {
            // Если URL относительный, добавляем базовый URL
            if (eventUrl.startsWith("/")) {
                eventUrl = "https://afisha.yandex.ru" + eventUrl;
            } else {
                eventUrl = "https://afisha.yandex.ru/" + eventUrl;
            }
        }

        // Создаем и возвращаем объект Event
        return Event.builder()
                .name(name)
                .date(date)
                .location(location)
                .price(price)
                .participantsCount(participantsCount)
                .organizerName(organizerName)
                .organizerContact(organizerContact)
                .sourceUrl(eventUrl != null && !eventUrl.isEmpty() ? eventUrl : sourceUrl)
                .build();
    }

    /**
     * Parse a date string from Yandex Afisha format.
     *
     * @param dateStr The date string to parse
     * @return The parsed LocalDateTime
     * 
     * Парсит строку даты в формате Яндекс Афиши.
     *
     * @param dateStr Строка с датой для парсинга
     * @return Объект LocalDateTime
     */
    private LocalDateTime parseYandexAfishaDate(String dateStr) {
        // Примеры форматов: "15 июня, 19:00", "Сегодня, 19:00", "Завтра, 20:00"
        LocalDateTime now = LocalDateTime.now();

        if (dateStr.contains("Сегодня")) {
            // Если "Сегодня", используем текущую дату
            String timeStr = dateStr.replaceAll(".*,\\s*", "").trim();
            String[] timeParts = timeStr.split(":");
            if (timeParts.length == 2) {
                return now
                        .withHour(Integer.parseInt(timeParts[0]))
                        .withMinute(Integer.parseInt(timeParts[1]))
                        .withSecond(0)
                        .withNano(0);
            }
            return now;
        } else if (dateStr.contains("Завтра")) {
            // Если "Завтра", используем текущую дату + 1 день
            String timeStr = dateStr.replaceAll(".*,\\s*", "").trim();
            String[] timeParts = timeStr.split(":");
            if (timeParts.length == 2) {
                return now.plusDays(1)
                        .withHour(Integer.parseInt(timeParts[0]))
                        .withMinute(Integer.parseInt(timeParts[1]))
                        .withSecond(0)
                        .withNano(0);
            }
            return now.plusDays(1);
        } else {
            // Пытаемся парсить полную дату
            // Преобразуем названия месяцев на русском в числовой формат
            String normalizedDateStr = dateStr
                    .replace("января", "01")
                    .replace("февраля", "02")
                    .replace("марта", "03")
                    .replace("апреля", "04")
                    .replace("мая", "05")
                    .replace("июня", "06")
                    .replace("июля", "07")
                    .replace("августа", "08")
                    .replace("сентября", "09")
                    .replace("октября", "10")
                    .replace("ноября", "11")
                    .replace("декабря", "12");

            // Извлекаем день, месяц, год и время
            Pattern pattern = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+)(?:,\\s*(\\d+):(\\d+))?");
            Matcher matcher = pattern.matcher(normalizedDateStr);

            if (matcher.find()) {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));

                int hour = 0;
                int minute = 0;

                if (matcher.groupCount() >= 5 && matcher.group(4) != null && matcher.group(5) != null) {
                    hour = Integer.parseInt(matcher.group(4));
                    minute = Integer.parseInt(matcher.group(5));
                }

                return LocalDateTime.of(year, month, day, hour, minute);
            }

            // Если не удалось распарсить, возвращаем текущую дату
            log.warn("Could not parse Yandex Afisha date: {}, using current date", dateStr);
            return now;
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

    /**
     * Parse an event element from TimepadRu website.
     *
     * @param element The HTML element representing an event from TimepadRu
     * @param sourceUrl The source URL of the page
     * @return The parsed Event object, or null if parsing failed
     * 
     * Парсит элемент мероприятия с сайта TimepadRu.
     *
     * @param element HTML-элемент, представляющий мероприятие с TimepadRu
     * @param sourceUrl URL-адрес источника страницы
     * @return Объект Event с данными мероприятия или null, если парсинг не удался
     */
    private Event parseTimepadRuEventElement(Element element, String sourceUrl) {
        log.debug("Parsing TimepadRu event element: {}", element.outerHtml().substring(0, Math.min(100, element.outerHtml().length())));

        // Извлекаем название мероприятия
        String name = extractText(element, "h2.event-card__title, .event-name, .event-list__item-title");
        if (name == null || name.isEmpty()) {
            name = extractText(element, "a.event-card__link, a.event-list__item-link");
        }
        if (name == null || name.isEmpty()) {
            // Пробуем извлечь из URL
            String eventUrl = element.select("a").attr("href");
            if (eventUrl != null && !eventUrl.isEmpty()) {
                // Извлекаем название из URL (часть после последнего /)
                String[] parts = eventUrl.split("/");
                if (parts.length > 0) {
                    String lastPart = parts[parts.length - 1];
                    // Удаляем параметры запроса
                    if (lastPart.contains("?")) {
                        lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                    }
                    // Заменяем дефисы на пробелы и делаем первую букву заглавной
                    name = lastPart.replace("-", " ");
                    if (name.length() > 0) {
                        name = name.substring(0, 1).toUpperCase() + name.substring(1);
                    }
                }
            }

            if (name == null || name.isEmpty()) {
                log.warn("Skipping TimepadRu event with no name");
                return null;
            }
        }

        // Извлекаем дату мероприятия
        String dateStr = extractText(element, ".event-card__date, .event-list__item-date, .event-date");
        if (dateStr == null || dateStr.isEmpty()) {
            dateStr = extractText(element, "time, .date");
        }

        LocalDateTime date = null;

        if (dateStr != null && !dateStr.isEmpty()) {
            // Пытаемся парсить дату в формате TimepadRu
            try {
                // Предполагаем, что дата в формате "день месяц, время" (например, "15 июня, 19:00")
                // Добавляем текущий год, так как он может отсутствовать
                String fullDateStr = dateStr + " " + LocalDateTime.now().getYear();
                date = parseYandexAfishaDate(fullDateStr); // Используем тот же метод, что и для Яндекс Афиши
            } catch (Exception e) {
                log.warn("Error parsing TimepadRu date: {}", dateStr, e);
            }
        }

        if (date == null) {
            // Если не удалось распарсить дату, используем текущую дату + 1 день
            date = LocalDateTime.now().plusDays(1);
            log.warn("Using default date for TimepadRu event: {}", date);
        }

        // Извлекаем место проведения
        String location = extractText(element, ".event-card__location, .event-list__item-location, .event-venue");
        if (location == null || location.isEmpty()) {
            location = extractText(element, ".location, .venue");
        }
        if (location == null || location.isEmpty()) {
            // Пробуем найти адрес в любом тексте
            Elements allText = element.select("*");
            for (Element e : allText) {
                String text = e.text();
                if (text.contains("Москва") || text.contains("ул.") || text.contains("пр-т")) {
                    location = text;
                    break;
                }
            }
        }
        if (location == null || location.isEmpty()) {
            location = "Москва"; // Используем дефолтное значение, так как мы парсим события в Москве
        }

        // Извлекаем цену (если есть)
        String priceStr = extractText(element, ".event-card__price, .event-list__item-price, .event-price");
        if (priceStr == null || priceStr.isEmpty()) {
            priceStr = extractText(element, ".price, span:contains(₽), span:contains(руб)");
        }
        BigDecimal price = parsePrice(priceStr);

        // Для TimepadRu часто нет информации о количестве участников
        Integer participantsCount = null;

        // Извлекаем имя организатора (если есть)
        String organizerName = extractText(element, ".event-card__organizer, .event-list__item-organizer, .event-organizer");

        // Для контактов организатора используем дефолтное значение, так как на странице списка событий
        // обычно нет контактной информации
        String organizerContact = "info@timepad.ru"; // Дефолтный контакт

        // Получаем URL события для дополнительной информации
        String eventUrl = element.select("a").attr("href");
        if (eventUrl != null && !eventUrl.isEmpty() && !eventUrl.startsWith("http")) {
            // Если URL относительный, добавляем базовый URL
            if (eventUrl.startsWith("/")) {
                eventUrl = "https://timepad.ru" + eventUrl;
            } else {
                eventUrl = "https://timepad.ru/" + eventUrl;
            }
        }

        // Создаем и возвращаем объект Event
        return Event.builder()
                .name(name)
                .date(date)
                .location(location)
                .price(price)
                .participantsCount(participantsCount)
                .organizerName(organizerName)
                .organizerContact(organizerContact)
                .sourceUrl(eventUrl != null && !eventUrl.isEmpty() ? eventUrl : sourceUrl)
                .build();
    }

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

    /**
     * Parse an event element from KudaGo.com website.
     *
     * @param element The HTML element representing an event from KudaGo.com
     * @param sourceUrl The source URL of the page
     * @return The parsed Event object, or null if parsing failed
     * 
     * Парсит элемент мероприятия с сайта KudaGo.com.
     *
     * @param element HTML-элемент, представляющий мероприятие с KudaGo.com
     * @param sourceUrl URL-адрес источника страницы
     * @return Объект Event с данными мероприятия или null, если парсинг не удался
     */
    private Event parseKudaGoEventElement(Element element, String sourceUrl) {
        log.debug("Parsing KudaGo.com event element: {}", element.outerHtml().substring(0, Math.min(100, element.outerHtml().length())));

        // Извлекаем название мероприятия
        String name = extractText(element, "h2.title, .event-card__title, .post-title");
        if (name == null || name.isEmpty()) {
            name = extractText(element, "a.title, a.event-card__link");
        }
        if (name == null || name.isEmpty()) {
            // Пробуем извлечь из URL
            String eventUrl = element.select("a").attr("href");
            if (eventUrl != null && !eventUrl.isEmpty()) {
                // Извлекаем название из URL (часть после последнего /)
                String[] parts = eventUrl.split("/");
                if (parts.length > 0) {
                    String lastPart = parts[parts.length - 1];
                    // Удаляем параметры запроса
                    if (lastPart.contains("?")) {
                        lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                    }
                    // Заменяем дефисы на пробелы и делаем первую букву заглавной
                    name = lastPart.replace("-", " ");
                    if (name.length() > 0) {
                        name = name.substring(0, 1).toUpperCase() + name.substring(1);
                    }
                }
            }

            if (name == null || name.isEmpty()) {
                log.warn("Skipping KudaGo.com event with no name");
                return null;
            }
        }

        // Извлекаем дату мероприятия
        String dateStr = extractText(element, ".date, .event-card__date, .post-date, time");
        if (dateStr == null || dateStr.isEmpty()) {
            dateStr = extractText(element, ".event-date, .event-datetime");
        }

        LocalDateTime date = null;

        if (dateStr != null && !dateStr.isEmpty()) {
            // Пытаемся парсить дату в формате KudaGo.com
            try {
                // Предполагаем, что дата в формате "день месяц, время" или "день месяц — день месяц"
                date = parseKudaGoDate(dateStr);
            } catch (Exception e) {
                log.warn("Error parsing KudaGo.com date: {}", dateStr, e);
            }
        }

        if (date == null) {
            // Если не удалось распарсить дату, используем текущую дату + 1 день
            date = LocalDateTime.now().plusDays(1);
            log.warn("Using default date for KudaGo.com event: {}", date);
        }

        // Извлекаем место проведения
        String location = extractText(element, ".place, .event-card__place, .post-place, .location");
        if (location == null || location.isEmpty()) {
            location = extractText(element, ".event-place, .venue");
        }
        if (location == null || location.isEmpty()) {
            // Пробуем найти адрес в любом тексте
            Elements allText = element.select("*");
            for (Element e : allText) {
                String text = e.text();
                if (text.contains("Москва") || text.contains("ул.") || text.contains("пр-т")) {
                    location = text;
                    break;
                }
            }
        }
        if (location == null || location.isEmpty()) {
            location = "Москва"; // Используем дефолтное значение, так как мы парсим события в Москве
        }

        // Извлекаем цену (если есть)
        String priceStr = extractText(element, ".price, .event-card__price, .post-price");
        if (priceStr == null || priceStr.isEmpty()) {
            priceStr = extractText(element, "span:contains(₽), span:contains(руб)");
        }
        if (priceStr == null || priceStr.isEmpty()) {
            // Пробуем найти цену в любом тексте
            Elements allText = element.select("*");
            for (Element e : allText) {
                String text = e.text();
                if (text.contains("₽") || text.contains("руб") || text.contains("от ")) {
                    priceStr = text;
                    break;
                }
            }
        }
        BigDecimal price = parsePrice(priceStr);

        // Для KudaGo.com часто нет информации о количестве участников
        Integer participantsCount = null;

        // Извлекаем имя организатора (если есть)
        String organizerName = extractText(element, ".organizer, .event-card__organizer, .post-organizer");

        // Для контактов организатора используем дефолтное значение, так как на странице списка событий
        // обычно нет контактной информации
        String organizerContact = "info@kudago.com"; // Дефолтный контакт

        // Получаем URL события для дополнительной информации
        String eventUrl = element.select("a").attr("href");
        if (eventUrl != null && !eventUrl.isEmpty() && !eventUrl.startsWith("http")) {
            // Если URL относительный, добавляем базовый URL
            if (eventUrl.startsWith("/")) {
                eventUrl = "https://kudago.com" + eventUrl;
            } else {
                eventUrl = "https://kudago.com/" + eventUrl;
            }
        }

        // Создаем и возвращаем объект Event
        return Event.builder()
                .name(name)
                .date(date)
                .location(location)
                .price(price)
                .participantsCount(participantsCount)
                .organizerName(organizerName)
                .organizerContact(organizerContact)
                .sourceUrl(eventUrl != null && !eventUrl.isEmpty() ? eventUrl : sourceUrl)
                .build();
    }

    /**
     * Parse a date string from KudaGo.com format.
     *
     * @param dateStr The date string to parse
     * @return The parsed LocalDateTime
     * 
     * Парсит строку даты в формате KudaGo.com.
     *
     * @param dateStr Строка с датой для парсинга
     * @return Объект LocalDateTime
     */
    private LocalDateTime parseKudaGoDate(String dateStr) {
        // Примеры форматов: "15 июня, 19:00", "15 июня — 20 июня", "Сегодня, 19:00", "Завтра, 20:00"
        LocalDateTime now = LocalDateTime.now();

        if (dateStr.contains("Сегодня")) {
            // Если "Сегодня", используем текущую дату
            String timeStr = dateStr.replaceAll(".*,\\s*", "").trim();
            String[] timeParts = timeStr.split(":");
            if (timeParts.length == 2) {
                return now
                        .withHour(Integer.parseInt(timeParts[0]))
                        .withMinute(Integer.parseInt(timeParts[1]))
                        .withSecond(0)
                        .withNano(0);
            }
            return now;
        } else if (dateStr.contains("Завтра")) {
            // Если "Завтра", используем текущую дату + 1 день
            String timeStr = dateStr.replaceAll(".*,\\s*", "").trim();
            String[] timeParts = timeStr.split(":");
            if (timeParts.length == 2) {
                return now.plusDays(1)
                        .withHour(Integer.parseInt(timeParts[0]))
                        .withMinute(Integer.parseInt(timeParts[1]))
                        .withSecond(0)
                        .withNano(0);
            }
            return now.plusDays(1);
        } else {
            // Пытаемся парсить полную дату
            // Преобразуем названия месяцев на русском в числовой формат
            String normalizedDateStr = dateStr
                    .replace("января", "01")
                    .replace("февраля", "02")
                    .replace("марта", "03")
                    .replace("апреля", "04")
                    .replace("мая", "05")
                    .replace("июня", "06")
                    .replace("июля", "07")
                    .replace("августа", "08")
                    .replace("сентября", "09")
                    .replace("октября", "10")
                    .replace("ноября", "11")
                    .replace("декабря", "12");

            // Если есть диапазон дат (например, "15 июня — 20 июня"), берем первую дату
            if (normalizedDateStr.contains("—")) {
                normalizedDateStr = normalizedDateStr.split("—")[0].trim();
            }

            // Извлекаем день, месяц и время
            Pattern pattern = Pattern.compile("(\\d+)\\s+(\\d+)(?:,\\s*(\\d+):(\\d+))?");
            Matcher matcher = pattern.matcher(normalizedDateStr);

            if (matcher.find()) {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = now.getYear(); // Используем текущий год, так как он часто не указан

                int hour = 0;
                int minute = 0;

                if (matcher.groupCount() >= 4 && matcher.group(3) != null && matcher.group(4) != null) {
                    hour = Integer.parseInt(matcher.group(3));
                    minute = Integer.parseInt(matcher.group(4));
                }

                return LocalDateTime.of(year, month, day, hour, minute);
            }

            // Если не удалось распарсить, возвращаем текущую дату
            log.warn("Could not parse KudaGo.com date: {}, using current date", dateStr);
            return now;
        }
    }
}
