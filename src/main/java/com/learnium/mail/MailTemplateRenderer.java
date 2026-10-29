package com.learnium.mail;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Renders mail templates (by outbox template key) and derives text fallbacks. */
@Component
public class MailTemplateRenderer {

    private final TemplateEngine mailTemplateEngine;

    public MailTemplateRenderer(@Qualifier("mailTemplateEngine") TemplateEngine mailTemplateEngine) {
        this.mailTemplateEngine = mailTemplateEngine;
    }

    public String render(String template, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        return mailTemplateEngine.process(template, context);
    }

    /**
     * Plain-text fallback derived from the HTML body: line structure is kept
     * (paragraphs and breaks become newlines), tags and the handful of HTML
     * entities the templates can produce are normalised.
     */
    public String toPlainText(String html) {
        String text =
                html.replaceAll("(?i)<br\\s*/?>", "\n")
                        .replaceAll("(?i)</p>", "\n\n")
                        .replaceAll("<[^>]+>", "");
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
