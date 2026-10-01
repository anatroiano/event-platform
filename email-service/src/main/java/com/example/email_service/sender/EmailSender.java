package com.example.email_service.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender javaMailSender;
    private final String senderEmail;

    public EmailSender(JavaMailSender javaMailSender, @Value("${app.mail.from}") String senderEmail) {
        this.javaMailSender = javaMailSender;
        this.senderEmail = senderEmail;
    }

    public void send(String to, String subject, String htmlBody) {

        log.info("Sending email: to={}, subject={}", to, subject);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            javaMailSender.send(message);

            log.info("Email sent successfully: to={}, subject={}", to, subject);
        } catch (MessagingException | MailException ex) {
            log.error("Failed to send email: to={}, subject={}", to, subject, ex);
            throw new EmailDeliveryException("Failed to send email to " + to, ex);
        }
    }
}

