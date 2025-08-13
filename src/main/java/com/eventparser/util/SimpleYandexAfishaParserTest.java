package com.eventparser.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Very simple test for parsing Yandex Afisha events without any dependencies.
 * This class only uses Jsoup to connect to the website and extract basic information.
 */
public class SimpleYandexAfishaParserTest {

    public static void main(String[] args) {
        // URL для парсинга
        String url = "https://afisha.yandex.ru/moscow/events";
        
        System.out.println("Starting very simple Yandex Afisha parser test...");
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
            
            // Создаем список для хранения информации о событиях
            List<EventInfo> events = new ArrayList<>();
            
            // Парсим события
            for (Element element : eventElements) {
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
                }
                
                // Извлекаем дату мероприятия
                String date = extractText(element, "time");
                if (date == null || date.isEmpty()) {
                    date = extractText(element, "div[data-test-id='eventDate']");
                }
                if (date == null || date.isEmpty()) {
                    date = extractText(element, "span:contains(сегодня), span:contains(завтра)");
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
                    location = "Москва"; // Используем дефолтное значение
                }
                
                // Извлекаем цену (если есть)
                String price = extractText(element, "div[data-test-id='eventPrice']");
                if (price == null || price.isEmpty()) {
                    price = extractText(element, "span:contains(₽), span:contains(руб)");
                }
                if (price == null || price.isEmpty()) {
                    // Пробуем найти цену в любом тексте
                    Elements allText = element.select("*");
                    for (Element e : allText) {
                        String text = e.text();
                        if (text.contains("₽") || text.contains("руб") || text.contains("от ")) {
                            price = text;
                            break;
                        }
                    }
                }
                
                // Получаем URL события
                String eventUrl = element.select("a").attr("href");
                if (eventUrl != null && !eventUrl.isEmpty() && !eventUrl.startsWith("http")) {
                    // Если URL относительный, добавляем базовый URL
                    if (eventUrl.startsWith("/")) {
                        eventUrl = "https://afisha.yandex.ru" + eventUrl;
                    } else {
                        eventUrl = "https://afisha.yandex.ru/" + eventUrl;
                    }
                }
                
                // Создаем объект с информацией о событии и добавляем в список
                EventInfo eventInfo = new EventInfo(name, date, location, price, eventUrl);
                events.add(eventInfo);
            }
            
            System.out.println("Parsed " + events.size() + " events.");
            
            // Выводим информацию о каждом событии
            for (int i = 0; i < events.size(); i++) {
                EventInfo event = events.get(i);
                System.out.println("\nEvent #" + (i + 1) + ":");
                System.out.println("Name: " + (event.getName() != null ? event.getName() : "Not found"));
                System.out.println("Date: " + (event.getDate() != null ? event.getDate() : "Not found"));
                System.out.println("Location: " + (event.getLocation() != null ? event.getLocation() : "Not found"));
                System.out.println("Price: " + (event.getPrice() != null ? event.getPrice() : "Not found"));
                System.out.println("URL: " + (event.getUrl() != null ? event.getUrl() : "Not found"));
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
     * Extract text from an element using a CSS selector.
     */
    private static String extractText(Element element, String selector) {
        Element selectedElement = element.select(selector).first();
        return selectedElement != null ? selectedElement.text().trim() : null;
    }
    
    /**
     * Simple class to store event information.
     */
    static class EventInfo {
        private final String name;
        private final String date;
        private final String location;
        private final String price;
        private final String url;
        
        public EventInfo(String name, String date, String location, String price, String url) {
            this.name = name;
            this.date = date;
            this.location = location;
            this.price = price;
            this.url = url;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDate() {
            return date;
        }
        
        public String getLocation() {
            return location;
        }
        
        public String getPrice() {
            return price;
        }
        
        public String getUrl() {
            return url;
        }
    }
}