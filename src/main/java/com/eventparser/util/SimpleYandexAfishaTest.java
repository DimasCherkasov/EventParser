package com.eventparser.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

/**
 * Simple test to verify we can connect to Yandex Afisha and extract event information.
 * This class doesn't depend on Spring or other complex dependencies.
 */
public class SimpleYandexAfishaTest {

    public static void main(String[] args) {
        // URL для парсинга
        String url = "https://afisha.yandex.ru/moscow/events";

        System.out.println("Starting simple Yandex Afisha test...");
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

            // Пробуем разные селекторы для поиска событий
            String[] selectors = {
                "div[data-component='EventCard']",
                "div[data-test-id='eventCard.root']",
                ".kzfGcP"
            };

            for (String selector : selectors) {
                Elements elements = doc.select(selector);
                System.out.println("Selector '" + selector + "' found " + elements.size() + " elements");

                // Если нашли элементы, выводим информацию о первых 3
                if (elements.size() > 0) {
                    System.out.println("Found events with selector: " + selector);

                    int count = Math.min(3, elements.size());
                    for (int i = 0; i < count; i++) {
                        Element element = elements.get(i);
                        System.out.println("\nEvent #" + (i + 1) + ":");

                        // Пробуем разные селекторы для названия
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
                        System.out.println("Name: " + (name != null ? name : "Not found"));

                        // Пробуем разные селекторы для даты
                        String date = extractText(element, "time");
                        if (date == null || date.isEmpty()) {
                            date = extractText(element, "div[data-test-id='eventDate']");
                        }
                        if (date == null || date.isEmpty()) {
                            date = extractText(element, "span:contains(сегодня), span:contains(завтра)");
                        }
                        System.out.println("Date: " + (date != null ? date : "Not found"));

                        // Пробуем разные селекторы для места
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
                        System.out.println("Location: " + (location != null ? location : "Not found"));

                        // Пробуем разные селекторы для цены
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
                        System.out.println("Price: " + (price != null ? price : "Not found"));

                        // Получаем URL события
                        String eventUrl = element.select("a").attr("href");
                        System.out.println("URL: " + (eventUrl != null ? eventUrl : "Not found"));

                        // Выводим часть HTML для анализа
                        System.out.println("HTML snippet: " + element.outerHtml().substring(0, Math.min(200, element.outerHtml().length())) + "...");
                    }

                    // Если нашли элементы с этим селектором, прекращаем поиск
                    break;
                }
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
     *
     * @param element The element to extract text from
     * @param selector The CSS selector to use
     * @return The extracted text, or null if no matching element was found
     */
    private static String extractText(Element element, String selector) {
        Element selectedElement = element.select(selector).first();
        return selectedElement != null ? selectedElement.text().trim() : null;
    }
}
