package com.example.email_service.service;

import com.example.email_service.messaging.event.EmailNotificationEvent;
import com.example.email_service.sender.EmailSender;
import com.example.email_service.template.EmailTemplateResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

    private final ProcessedMessageService processedMessageService;
    private final EmailTemplateResolver templateResolver;
    private final EmailSender emailSender;

    public void dispatch(EmailNotificationEvent event) {

        final String messageId = event.messageId();
        final String templateId = event.templateId();
        final String recipientEmail = event.recipientEmail();

        log.info("Dispatching email notification: messageId={}, template={}, recipient={}",
                messageId, templateId, recipientEmail);

        if (!processedMessageService.tryClaim(messageId)) {
            log.info("Message already claimed by another delivery, skipping: messageId={}", messageId);
            return;
        }

        try {
            String subject = templateResolver.resolveSubject(templateId);
            String htmlBody = templateResolver.render(templateId, event.templateData());

            emailSender.send(recipientEmail, subject, htmlBody);

            log.info("Email notification dispatched successfully: messageId={}", messageId);

        } catch (RuntimeException ex) {
            releaseClaim(messageId);
            throw ex;
        }
    }

    private void releaseClaim(String messageId) {
        try {
            processedMessageService.release(messageId);
        } catch (RuntimeException ex) {
            log.error("Failed to release idempotency claim: messageId={}", messageId, ex);
        }
    }
}
