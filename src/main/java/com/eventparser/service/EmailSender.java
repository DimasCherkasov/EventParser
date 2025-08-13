package com.eventparser.service;

import com.eventparser.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Implementation of MessageSender for sending messages via email.
 * 
 * Реализация интерфейса MessageSender для отправки сообщений по электронной почте.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender implements MessageSender {

    private final JavaMailSender mailSender;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");

    @Override
    public boolean sendMessage(Event event, String message) {
        // Проверяем, можем ли мы обработать этот контакт (является ли он email-адресом)
        if (!canHandle(event.getOrganizerContact())) {
            log.warn("Cannot send email to non-email contact: {}", event.getOrganizerContact());
            return false;
        }

        try {
            // Создаем объект сообщения
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // Создаем помощник для работы с сообщением, включаем поддержку HTML и UTF-8
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // Устанавливаем получателя (email организатора мероприятия)
            helper.setTo(event.getOrganizerContact());
            // Устанавливаем тему письма
            helper.setSubject("Regarding Event: " + event.getName());

            // Создаем HTML-содержимое письма с деталями мероприятия
            String htmlContent = createEmailContent(event, message);
            // Устанавливаем текст письма (HTML)
            helper.setText(htmlContent, true);

            // Отправляем сообщение
            mailSender.send(mimeMessage);
            // Логируем успешную отправку
            log.info("Email sent successfully to: {}", event.getOrganizerContact());
            return true;
        } catch (MessagingException e) {
            // Логируем ошибку при отправке
            log.error("Failed to send email to: {}", event.getOrganizerContact(), e);
            return false;
        }
    }

    @Async // Аннотация для асинхронного выполнения метода
    @Override
    public CompletableFuture<Boolean> sendMessageAsync(Event event, String message) {
        // Асинхронная отправка сообщения (в текущей реализации просто вызывает синхронный метод)
        return CompletableFuture.completedFuture(sendMessage(event, message));
    }

    @Override
    public boolean canHandle(String contact) {
        // Проверяем, является ли контакт email-адресом с помощью регулярного выражения
        return contact != null && EMAIL_PATTERN.matcher(contact).matches();
    }

    /**
     * Create HTML content for the email.
     * 
     * Создает HTML-содержимое для электронного письма.
     *
     * @param event The event to create content for (Мероприятие, для которого создается содержимое)
     * @param message The message to include (Сообщение для включения в письмо)
     * @return HTML content for the email (HTML-содержимое для письма)
     */
    private String createEmailContent(Event event, String message) {
        // Создаем построитель строк для формирования HTML-содержимого
        StringBuilder builder = new StringBuilder();
        // Начало HTML-документа
        builder.append("<html><body>");
        // Заголовок с названием мероприятия
        builder.append("<h2>Regarding Event: ").append(event.getName()).append("</h2>");
        // Основное сообщение
        builder.append("<p>").append(message).append("</p>");
        // Подзаголовок для деталей мероприятия
        builder.append("<h3>Event Details:</h3>");
        // Начало списка деталей
        builder.append("<ul>");
        // Дата мероприятия
        builder.append("<li><strong>Date:</strong> ").append(event.getDate()).append("</li>");
        // Место проведения
        builder.append("<li><strong>Location:</strong> ").append(event.getLocation()).append("</li>");

        // Добавляем цену, если она указана
        if (event.getPrice() != null) {
            builder.append("<li><strong>Price:</strong> ").append(event.getPrice()).append("</li>");
        }

        // Добавляем количество участников, если оно указано
        if (event.getParticipantsCount() != null) {
            builder.append("<li><strong>Participants:</strong> ").append(event.getParticipantsCount()).append("</li>");
        }

        // Закрываем список
        builder.append("</ul>");
        // Добавляем просьбу ответить на письмо
        builder.append("<p>Please reply to this email if you have any questions.</p>");
        // Закрываем HTML-документ
        builder.append("</body></html>");

        // Возвращаем готовое HTML-содержимое в виде строки
        return builder.toString();
    }
}
