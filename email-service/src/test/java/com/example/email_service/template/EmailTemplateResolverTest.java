package com.example.email_service.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTemplateResolver")
class EmailTemplateResolverTest {

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailTemplateResolver templateResolver;

    @Nested
    @DisplayName("resolveSubject()")
    class ResolveSubject {

        @Test
        @DisplayName("returns the correct subject for subscription-confirmation template")
        void returnsCorrectSubjectForKnownTemplate() {
            String subject = templateResolver.resolveSubject("subscription-confirmation");

            assertThat(subject).isEqualTo("Confirmação de inscrição");
        }

        @Test
        @DisplayName("throws UnknownTemplateException for null templateId")
        void throwsForNullTemplateId() {
            assertThatThrownBy(() -> templateResolver.resolveSubject(null))
                    .isInstanceOf(UnknownTemplateException.class);
        }

        @Test
        @DisplayName("throws UnknownTemplateException for unknown templateId")
        void throwsForUnknownTemplateId() {
            assertThatThrownBy(() -> templateResolver.resolveSubject("non-existent-template"))
                    .isInstanceOf(UnknownTemplateException.class);
        }
    }

    @Nested
    @DisplayName("render()")
    class Render {

        @Test
        @DisplayName("delegates to Thymeleaf engine with the correct templateId")
        void delegatesToThymeleafEngine() {
            given(templateEngine.process(eq("subscription-confirmation"), any(Context.class)))
                    .willReturn("<html>rendered</html>");

            String result = templateResolver.render("subscription-confirmation", Map.of("key", "value"));

            assertThat(result).isEqualTo("<html>rendered</html>");
            then(templateEngine).should().process(eq("subscription-confirmation"), any(Context.class));
        }

        @Test
        @DisplayName("throws UnknownTemplateException before reaching the engine for unknown templateId")
        void throwsBeforeReachingEngineForUnknownTemplate() {
            assertThatThrownBy(() -> templateResolver.render("non-existent", Map.of()))
                    .isInstanceOf(UnknownTemplateException.class);

            then(templateEngine).shouldHaveNoInteractions();
        }
    }
}
