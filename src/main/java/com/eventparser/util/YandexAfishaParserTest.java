package com.eventparser.util;

import com.eventparser.model.Event;
import com.eventparser.parser.WebsiteEventParser;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Simple test class to verify the Yandex Afisha parser functionality.
 * 
 * Простой тестовый класс для проверки функциональности парсера Яндекс Афиши.
 */
public class YandexAfishaParserTest {

    @Configuration
    @ComponentScan("com.eventparser.parser")
    static class TestConfig {
        @Bean
        public String userAgent() {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
        }
        
        @Bean
        public Integer timeout() {
            return 10000;
        }
        
        @Bean
        public Integer threadCount() {
            return 5;
        }
    }

    public static void main(String[] args) {
        // URL для парсинга
        String url = "https://afisha.yandex.ru/moscow/events";
        
        System.out.println("Starting Yandex Afisha parser test...");
        System.out.println("URL: " + url);
        
        try {
            // Создаем контекст Spring для инициализации парсера
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
            
            // Получаем экземпляр парсера
            WebsiteEventParser parser = context.getBean(WebsiteEventParser.class);
            
            System.out.println("Parser initialized. Starting parsing...");
            
            // Парсим события
            List<Event> events = parser.parseEvents(url);
            
            System.out.println("Parsing completed. Found " + events.size() + " events.");
            
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
            
            // Закрываем контекст Spring
            context.close();
            
            System.out.println("\nTest completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}