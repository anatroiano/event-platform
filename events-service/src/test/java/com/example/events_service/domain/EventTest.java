package com.example.events_service.domain;

import com.example.events_service.dto.EventRequestDTO;
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

class EventTest {

    private static final Instant EVENT_DATE = Instant.parse("2030-06-01T10:00:00Z");
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

    @Test
    void rejectsMissingDate() {
        EventRequestDTO request = new EventRequestDTO(10, null, "Workshop", null);
        Event event = Event.from(request);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("date");
        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("date");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCapacity(int capacity) {
        EventRequestDTO request = new EventRequestDTO(capacity, EVENT_DATE, "Workshop", null);
        Event event = Event.from(request);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("maxParticipants");
        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("maxParticipants");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingOrBlankTitle(String title) {
        EventRequestDTO request = new EventRequestDTO(10, EVENT_DATE, title, null);
        Event event = Event.from(request);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("title");
        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("title");
    }

    @Test
    void rejectsTextExceedingSizeLimits() {
        EventRequestDTO request = new EventRequestDTO(10, EVENT_DATE, "a".repeat(256), "b".repeat(2001));
        Event event = Event.from(request);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "description");
        assertThat(validator.validate(event)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "description");
    }

    @Test
    void allowsMissingDescriptionAndPastDate() {
        EventRequestDTO request = new EventRequestDTO(10, Instant.parse("2020-01-01T00:00:00Z"), "Workshop", null);
        Event event = Event.from(request);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(event)).isEmpty();
    }
}
