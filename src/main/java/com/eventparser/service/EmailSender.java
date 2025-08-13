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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender implements MessageSender {

    private final JavaMailSender mailSender;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");

    @Override
    public boolean sendMessage(Event event, String message) {
        if (!canHandle(event.getOrganizerContact())) {
            log.warn("Cannot send email to non-email contact: {}", event.getOrganizerContact());
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(event.getOrganizerContact());
            helper.setSubject("Regarding Event: " + event.getName());
            
            // Create HTML content with event details
            String htmlContent = createEmailContent(event, message);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", event.getOrganizerContact());
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}", event.getOrganizerContact(), e);
            return false;
        }
    }

    @Async
    @Override
    public CompletableFuture<Boolean> sendMessageAsync(Event event, String message) {
        return CompletableFuture.completedFuture(sendMessage(event, message));
    }

    @Override
    public boolean canHandle(String contact) {
        return contact != null && EMAIL_PATTERN.matcher(contact).matches();
    }
    
    /**
     * Create HTML content for the email.
     *
     * @param event The event to create content for
     * @param message The message to include
     * @return HTML content for the email
     */
    private String createEmailContent(Event event, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body>");
        builder.append("<h2>Regarding Event: ").append(event.getName()).append("</h2>");
        builder.append("<p>").append(message).append("</p>");
        builder.append("<h3>Event Details:</h3>");
        builder.append("<ul>");
        builder.append("<li><strong>Date:</strong> ").append(event.getDate()).append("</li>");
        builder.append("<li><strong>Location:</strong> ").append(event.getLocation()).append("</li>");
        
        if (event.getPrice() != null) {
            builder.append("<li><strong>Price:</strong> ").append(event.getPrice()).append("</li>");
        }
        
        if (event.getParticipantsCount() != null) {
            builder.append("<li><strong>Participants:</strong> ").append(event.getParticipantsCount()).append("</li>");
        }
        
        builder.append("</ul>");
        builder.append("<p>Please reply to this email if you have any questions.</p>");
        builder.append("</body></html>");
        
        return builder.toString();
    }
}