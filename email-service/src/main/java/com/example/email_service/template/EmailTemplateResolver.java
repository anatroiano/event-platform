package com.example.email_service.template;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailTemplateResolver {

    private final TemplateEngine templateEngine;

    private static final Map<String, String> SUBJECTS = Map.of(
            "subscription-confirmation", "Confirmação de inscrição",
            "certificate", "Seu certificado de participação está disponível"
    );

    public String resolveSubject(String templateId) {
        requireKnownTemplate(templateId);
        return SUBJECTS.get(templateId);
    }

    public String render(String templateId, Map<String, Object> data) {
        requireKnownTemplate(templateId);

        Context context = new Context();
        Optional.ofNullable(data).ifPresent(context::setVariables);

        return templateEngine.process(templateId, context);
    }

    private static void requireKnownTemplate(String templateId) {
        Optional.ofNullable(templateId)
                .filter(SUBJECTS::containsKey)
                .orElseThrow(() -> new UnknownTemplateException(templateId));
    }
}

