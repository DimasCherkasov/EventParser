package com.eventparser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing an event parsed from the internet.
 * 
 * Сущность, представляющая мероприятие, спарсенное из интернета.
 */
@Entity // Аннотация, указывающая, что этот класс является сущностью JPA
@Table(name = "events") // Указывает имя таблицы в базе данных
@Data // Lombok: автоматически генерирует геттеры, сеттеры, equals, hashCode и toString
@Builder // Lombok: позволяет использовать паттерн Builder для создания объектов
@NoArgsConstructor // Lombok: генерирует конструктор без аргументов
@AllArgsConstructor // Lombok: генерирует конструктор со всеми аргументами
public class Event {

    @Id // Первичный ключ
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Автоинкремент ID
    private Long id; // Уникальный идентификатор мероприятия

    @Column(nullable = false) // Колонка не может быть NULL
    private String name; // Название мероприятия

    @Column(nullable = false)
    private LocalDateTime date; // Дата и время проведения мероприятия

    @Column(nullable = false)
    private String location; // Место проведения мероприятия

    @Column
    private BigDecimal price; // Цена (если указана)

    @Column(name = "participants_count")
    private Integer participantsCount; // Количество участников (если указано)

    @Column(name = "organizer_name")
    private String organizerName; // Имя организатора

    @Column(name = "organizer_contact", nullable = false)
    private String organizerContact; // Контакт организатора (email, телефон, Telegram)

    @Column(name = "message_sent", nullable = false)
    private boolean messageSent = false; // Флаг, указывающий, было ли отправлено сообщение организатору

    @Column(name = "response_received", nullable = false)
    private boolean responseReceived = false; // Флаг, указывающий, был ли получен ответ от организатора

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // Дата и время создания записи

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // Дата и время последнего обновления записи

    @Column(name = "source_url")
    private String sourceUrl; // URL источника, откуда было спарсено мероприятие

    @PrePersist // Вызывается перед сохранением новой сущности
    protected void onCreate() {
        createdAt = LocalDateTime.now(); // Устанавливаем текущее время как время создания
    }

    @PreUpdate // Вызывается перед обновлением существующей сущности
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(); // Устанавливаем текущее время как время обновления
    }
}
