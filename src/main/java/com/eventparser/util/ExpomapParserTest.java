package com.eventparser.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Random;

/**
 * Тестовый класс для анализа структуры HTML-страницы сайта expomap.ru
 * и определения правильных селекторов для парсинга мероприятий.
 */
public class ExpomapParserTest {

    // Массив различных User-Agent для ротации
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0"
    };

    public static void main(String[] args) {
        try {
            // URL сайта expomap.ru для анализа
            String url = "https://expomap.ru/expo/city/moscow/";
            
            // Выбираем случайный User-Agent из массива для ротации
            String randomUserAgent = USER_AGENTS[new Random().nextInt(USER_AGENTS.length)];
            System.out.println("[DEBUG_LOG] Using User-Agent: " + randomUserAgent);
            
            // Подключаемся к URL и получаем HTML-документ
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUserAgent)
                    .timeout(30000)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "max-age=0")
                    .get();
            
            System.out.println("[DEBUG_LOG] Успешно загружена страница: " + url);
            
            // Анализируем текущие селекторы
            analyzeSelectors(doc, ".expo-item, .expo-card, .event-card, div[class*='expo'], div[class*='event']");
            
            // Пробуем другие селекторы
            System.out.println("\n[DEBUG_LOG] Пробуем альтернативные селекторы:");
            analyzeSelectors(doc, ".event-list-item, .event-item, .item, .card, article");
            
            // Анализируем структуру страницы для поиска элементов с мероприятиями
            System.out.println("\n[DEBUG_LOG] Анализ структуры страницы:");
            analyzePageStructure(doc);
            
        } catch (IOException e) {
            System.err.println("[DEBUG_LOG] Ошибка при загрузке страницы: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Анализирует результаты поиска по заданным селекторам
     */
    private static void analyzeSelectors(Document doc, String selectors) {
        Elements elements = doc.select(selectors);
        System.out.println("[DEBUG_LOG] Найдено элементов по селекторам '" + selectors + "': " + elements.size());
        
        if (elements.isEmpty()) {
            System.out.println("[DEBUG_LOG] Элементы не найдены по данным селекторам");
        } else {
            // Выводим информацию о первых 3 найденных элементах
            int count = Math.min(3, elements.size());
            for (int i = 0; i < count; i++) {
                Element element = elements.get(i);
                System.out.println("[DEBUG_LOG] Элемент #" + (i+1) + ":");
                System.out.println("[DEBUG_LOG] - Классы: " + element.className());
                System.out.println("[DEBUG_LOG] - Текст: " + element.text().substring(0, Math.min(100, element.text().length())));
                
                // Ищем название мероприятия
                String name = extractText(element, "h2, .expo-title, .expo-name, .title, .name");
                if (name.isEmpty()) {
                    name = element.select("a").attr("title");
                    if (name.isEmpty()) {
                        name = element.text();
                    }
                }
                System.out.println("[DEBUG_LOG] - Название: " + name);
                
                // Ищем дату проведения
                String dateStr = extractText(element, ".expo-date, .date, .event-date, time");
                System.out.println("[DEBUG_LOG] - Дата: " + dateStr);
                
                // Ищем место проведения
                String location = extractText(element, ".expo-place, .place, .location, .venue, .address");
                System.out.println("[DEBUG_LOG] - Место: " + location);
                
                // Ищем ссылку на мероприятие
                String eventUrl = element.select("a").attr("href");
                System.out.println("[DEBUG_LOG] - URL: " + eventUrl);
                
                System.out.println("[DEBUG_LOG] - HTML: " + element.outerHtml().substring(0, Math.min(200, element.outerHtml().length())));
                System.out.println();
            }
        }
    }
    
    /**
     * Анализирует общую структуру страницы для поиска элементов с мероприятиями
     */
    private static void analyzePageStructure(Document doc) {
        // Ищем основные контейнеры на странице
        Elements mainContainers = doc.select("div.container, div.content, main, section");
        System.out.println("[DEBUG_LOG] Найдено основных контейнеров: " + mainContainers.size());
        
        for (int i = 0; i < Math.min(3, mainContainers.size()); i++) {
            Element container = mainContainers.get(i);
            System.out.println("[DEBUG_LOG] Контейнер #" + (i+1) + ":");
            System.out.println("[DEBUG_LOG] - ID: " + container.id());
            System.out.println("[DEBUG_LOG] - Классы: " + container.className());
            System.out.println("[DEBUG_LOG] - Количество дочерних элементов: " + container.children().size());
            
            // Ищем списки или сетки с элементами
            Elements lists = container.select("ul, ol, div.list, div.grid, div.row");
            System.out.println("[DEBUG_LOG] - Найдено списков/сеток: " + lists.size());
            
            // Ищем карточки или элементы списка
            Elements items = container.select("li, div.item, div.card, article, div[class*='expo'], div[class*='event']");
            System.out.println("[DEBUG_LOG] - Найдено потенциальных элементов мероприятий: " + items.size());
            
            if (items.size() > 0) {
                Element item = items.get(0);
                System.out.println("[DEBUG_LOG] - Пример элемента: " + item.outerHtml().substring(0, Math.min(200, item.outerHtml().length())));
            }
            
            System.out.println();
        }
        
        // Ищем все ссылки, содержащие ключевые слова, связанные с выставками
        Elements expoLinks = doc.select("a:contains(выставка), a:contains(форум), a:contains(конференция), a:contains(expo), a:contains(event)");
        System.out.println("[DEBUG_LOG] Найдено ссылок с ключевыми словами: " + expoLinks.size());
        
        for (int i = 0; i < Math.min(5, expoLinks.size()); i++) {
            Element link = expoLinks.get(i);
            System.out.println("[DEBUG_LOG] Ссылка #" + (i+1) + ": " + link.text() + " - " + link.attr("href"));
        }
    }
    
    /**
     * Извлекает текст из элемента по селектору
     */
    private static String extractText(Element element, String selector) {
        Elements selectedElements = element.select(selector);
        if (!selectedElements.isEmpty()) {
            return selectedElements.first().text().trim();
        }
        return "";
    }
}