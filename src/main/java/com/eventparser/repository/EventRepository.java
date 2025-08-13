package com.eventparser.repository;

import com.eventparser.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing and manipulating Event entities.
 * 
 * Репозиторий для доступа и управления сущностями Event (Мероприятие).
 */
@Repository // Аннотация, указывающая, что этот интерфейс является репозиторием Spring Data
public interface EventRepository extends JpaRepository<Event, Long> { // Расширяет JpaRepository для работы с сущностью Event и ID типа Long

    /**
     * Find events that have not had messages sent to their organizers.
     *
     * @return List of events where message_sent is false
     * 
     * Находит мероприятия, для которых еще не были отправлены сообщения организаторам.
     * 
     * @return Список мероприятий, где message_sent равно false
     */
    List<Event> findByMessageSentFalse(); // Метод автоматически создает SQL-запрос на основе имени метода

    /**
     * Find events that have had messages sent but no response received.
     *
     * @return List of events where message_sent is true and response_received is false
     * 
     * Находит мероприятия, для которых были отправлены сообщения, но не получены ответы.
     * 
     * @return Список мероприятий, где message_sent равно true и response_received равно false
     */
    List<Event> findByMessageSentTrueAndResponseReceivedFalse();

    /**
     * Find events by location.
     *
     * @param location The location to search for
     * @return List of events at the specified location
     * 
     * Находит мероприятия по месту проведения.
     * 
     * @param location Место проведения для поиска
     * @return Список мероприятий в указанном месте
     */
    List<Event> findByLocationContainingIgnoreCase(String location); // Поиск без учета регистра с частичным совпадением

    /**
     * Find events occurring after a specific date.
     *
     * @param date The date after which to find events
     * @return List of events occurring after the specified date
     * 
     * Находит мероприятия, происходящие после определенной даты.
     * 
     * @param date Дата, после которой искать мероприятия
     * @return Список мероприятий, происходящих после указанной даты
     */
    List<Event> findByDateAfter(LocalDateTime date);

    /**
     * Find events by name containing the search term (case insensitive).
     *
     * @param name The name to search for
     * @return List of events with names containing the search term
     * 
     * Находит мероприятия по названию, содержащему поисковый запрос (без учета регистра).
     * 
     * @param name Название для поиска
     * @return Список мероприятий с названиями, содержащими поисковый запрос
     */
    List<Event> findByNameContainingIgnoreCase(String name);

    /**
     * Find events by organizer contact.
     *
     * @param contact The organizer contact to search for
     * @return List of events with the specified organizer contact
     * 
     * Находит мероприятия по контактам организатора.
     * 
     * @param contact Контакт организатора для поиска
     * @return Список мероприятий с указанным контактом организатора
     */
    List<Event> findByOrganizerContactContainingIgnoreCase(String contact);

    /**
     * Find events that are happening soon (within the next 7 days).
     *
     * @param now The current date and time
     * @param endDate The end date for the search range (now + 7 days)
     * @return List of events happening within the next 7 days
     * 
     * Находит мероприятия, которые скоро состоятся (в течение следующих 7 дней).
     * 
     * @param now Текущая дата и время
     * @param endDate Конечная дата для диапазона поиска (сейчас + 7 дней)
     * @return Список мероприятий, происходящих в течение следующих 7 дней
     */
    @Query("SELECT e FROM Event e WHERE e.date BETWEEN ?1 AND ?2 ORDER BY e.date ASC") // Пользовательский JPQL-запрос
    List<Event> findUpcomingEvents(LocalDateTime now, LocalDateTime endDate);
}
