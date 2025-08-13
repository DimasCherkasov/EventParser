package com.eventparser.util;

import com.eventparser.model.Event;
import com.eventparser.parser.WebsiteEventParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct test for the Yandex Afisha parser without Spring dependencies.
 * This class contains a simplified version of the WebsiteEventParser to test parsing Yandex Afisha events.
 */
public class YandexAfishaParserDirectTest {

    // Регулярные выражения для извлечения контактной информации
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9\\s\\-()]{10,20}");
    private static final Pattern TELEGRAM_PATTERN = Pattern.compile("@[a-zA-Z0-9_]{5,32}");

    public static void main(String[] args) {
        // URL для парсинга
        String url = "https://afisha.yandex.ru/moscow/events";
        
        System.out.println("Starting direct Yandex Afisha parser test...");
        System.out.println("URL: " + url);
        
        try {
            // Устанавливаем User-Agent
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
            
            // Подключаемся к URL и получаем HTML-документ
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(10000)
                    .get();
            
            System.out.println("Connected to Yandex Afisha successfully.");
            
            // Находим элементы событий
            Elements eventElements = doc.select("div[data-component='EventCard']");
            System.out.println("Found " + eventElements.size() + " event elements.");
            
            // Парсим события
            List<Event> events = new ArrayList<>();
            for (Element element : eventElements) {
                Event event = parseYandexAfishaEventElement(element, url);
                if (event != null) {
                    events.add(event);
                }
            }
            
            System.out.println("Parsed " + events.size() + " events.");
            
            // Выводим информацию о каждом событии
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                System.out.println("\nEvent #" + (i + 1) + ":");
                System.out.println("Name: " + event.getName());
                System.out.println("Date: " + event.getDate());
                System.out.println("Location: " + event.getLocation());
                System.out.println("Price: " + (event.getPrice() != null ? event.getPrice() : "Not specified"));
                System.out.println("Organizer: " + (event.getOrganizerName() != null ? event.getOrganizerName() : "Not specified"));
                System.out.println("Contact: " + event.getOrganizerContact());
                System.out.println("Source URL: " + event.getSourceUrl());
            }
            
            System.out.println("\nTest completed successfully.");
        } catch (IOException e) {
            System.err.println("Error connecting to Yandex Afisha: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Parse an event element from Yandex Afisha website.
     */
    private static Event parseYandexAfishaEventElement(Element element, String sourceUrl) {
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
                System.out.println("Skipping Yandex Afisha event with no name");
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
                System.out.println("Error parsing Yandex Afisha date: " + dateStr);
            }
        }
        
        if (date == null) {
            // Если не удалось распарсить дату, используем текущую дату + 1 день
            date = LocalDateTime.now().plusDays(1);
            System.out.println("Using default date for Yandex Afisha event: " + date);
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
     */
    private static LocalDateTime parseYandexAfishaDate(String dateStr) {
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
            System.out.println("Could not parse Yandex Afisha date: " + dateStr + ", using current date");
            return now;
        }
    }
    
    /**
     * Extract text from an element using a CSS selector.
     */
    private static String extractText(Element element, String selector) {
        Element selectedElement = element.select(selector).first();
        return selectedElement != null ? selectedElement.text().trim() : null;
    }
    
    /**
     * Parse a price string into a BigDecimal.
     */
    private static BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return null;
        }
        
        // Удаляем символы валюты и нечисловые символы, кроме десятичной точки
        String cleanPrice = priceStr.replaceAll("[^0-9.]", "");
        try {
            return new BigDecimal(cleanPrice);
        } catch (NumberFormatException e) {
            System.out.println("Could not parse price: " + priceStr);
            return null;
        }
    }
}