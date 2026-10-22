package com.example.email_service.messaging.event;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNotificationEventTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingMessageId(String messageId) {
        EmailNotificationEvent event = new EmailNotificationEvent(messageId, null,
                "subscription-confirmation", "user@example.com", null, null);

        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("messageId");
    }

    @Test
    void rejectsMessageIdExceedingColumnLength() {
        EmailNotificationEvent event = new EmailNotificationEvent("a".repeat(65), null,
                "subscription-confirmation", "user@example.com", null, null);

        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("messageId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid-email"})
    void rejectsInvalidRecipient(String recipient) {
        EmailNotificationEvent event = new EmailNotificationEvent("msg-001", null,
                "subscription-confirmation", recipient, null, null);

        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("recipientEmail");
    }

    @Test
    void rejectsMissingTemplate() {
        EmailNotificationEvent event = new EmailNotificationEvent("msg-001", null,
                null, "user@example.com", null, null);

        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("templateId");
    }

    @Test
    void allowsOptionalFieldsToBeAbsent() {
        EmailNotificationEvent event = new EmailNotificationEvent("msg-001", null,
                "subscription-confirmation", "user@example.com", null, null);

        assertThat(validator.validate(event)).isEmpty();
    }
}
