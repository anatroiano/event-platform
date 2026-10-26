package com.example.events_service.domain;

import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.dto.SubscriptionRequestDTO;
import com.example.events_service.exception.InvalidDataException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

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

    private Event buildEvent() {
        return Event.from(new EventRequestDTO(10, Instant.parse("2030-06-01T10:00:00Z"), "Workshop", null));
    }

    @Test
    void rejectsMissingEvent() {
        Subscription subscription = Subscription.from(null, "user@example.com", "User");

        assertThat(validator.validate(subscription)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("event");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "invalid-email"})
    void rejectsInvalidEmail(String email) {
        Event event = buildEvent();

        SubscriptionRequestDTO request = new SubscriptionRequestDTO("User", email);
        Subscription subscription = Subscription.from(event, email, "User");

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("participantEmail");
        assertThat(validator.validate(subscription)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("participantEmail");
    }

    @Test
    void allowsMissingParticipantName() {
        Subscription subscription = Subscription.from(buildEvent(), "user@example.com", null);

        assertThat(validator.validate(new SubscriptionRequestDTO(null, "user@example.com"))).isEmpty();
        assertThat(validator.validate(subscription)).isEmpty();
    }

    @Test
    void rejectsTextExceedingColumnLength() {
        String name = "a".repeat(256);
        String email = "a".repeat(244) + "@example.com";
        SubscriptionRequestDTO request = new SubscriptionRequestDTO(name, email);
        Subscription subscription = Subscription.from(buildEvent(), email, name);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("participantName", "participantEmail");
        assertThat(validator.validate(subscription)).extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly("participantName", "participantEmail");
    }

    @Test
    void rejectsMissingCheckinTimeWithoutChangingAttendance() {
        Subscription subscription = Subscription.from(buildEvent(), "user@example.com", "User");

        assertThatThrownBy(() -> subscription.checkIn(null))
                .isInstanceOf(InvalidDataException.class);

        assertThat(subscription.getCheckedInAt()).isNull();
    }

    @Test
    void preservesFirstCheckinTime() {
        Subscription subscription = Subscription.from(buildEvent(), "user@example.com", "User");
        Instant firstCheckinTime = Instant.parse("2026-01-01T12:00:00Z");

        subscription.checkIn(firstCheckinTime);
        subscription.checkIn(firstCheckinTime.plusSeconds(60));

        assertThat(subscription.getCheckedInAt()).isEqualTo(firstCheckinTime);
    }
}
