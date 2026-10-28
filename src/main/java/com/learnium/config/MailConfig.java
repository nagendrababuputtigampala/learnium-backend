package com.learnium.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Standalone Thymeleaf engine for transactional mail, deliberately separate
 * from the web template engine: mail rendering must work in the outbox
 * dispatcher's scheduled thread with no servlet context involved. The Spring
 * engine variant is used because it evaluates expressions with SpringEL —
 * the standalone engine's OGNL evaluator is not on this classpath.
 */
@Configuration
public class MailConfig {

    @Bean
    public TemplateEngine mailTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
