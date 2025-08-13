package com.eventparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Event Parser application.
 * This application parses events from various sources, processes them,
 * and stores them in a PostgreSQL database.
 * 
 * Главный класс приложения для парсинга мероприятий.
 * Это приложение парсит мероприятия из различных источников, обрабатывает их,
 * и сохраняет в базу данных PostgreSQL.
 */
@SpringBootApplication // Аннотация, которая включает автоконфигурацию Spring Boot
@EnableScheduling // Включает возможность планирования задач (для периодического парсинга мероприятий)
public class EventParserApplication {

    public static void main(String[] args) {
        // Запуск Spring Boot приложения
        SpringApplication.run(EventParserApplication.class, args);
    }
}
