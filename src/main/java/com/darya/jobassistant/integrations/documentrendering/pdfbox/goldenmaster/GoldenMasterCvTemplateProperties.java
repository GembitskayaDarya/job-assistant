package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sprint 11 Golden Master Template Rendering: binds {@code cv-golden-master-template.path} - the
 * filesystem location of the single, private, approved reference CV PDF ({@link
 * GoldenMasterCvTemplate} loads and reuses it as the literal production template; only its Technical
 * Skills line is ever replaced - see that class's javadoc). Deliberately a plain path property, not
 * a {@code spring.config.import}: the golden master is a binary PDF, not a YAML document to bind
 * configuration from, so it is read directly as a file by {@link GoldenMasterCvTemplate} rather than
 * parsed by Spring Boot's config-import machinery (the pattern {@code baseline-cv-selection.yml}
 * uses, which only works for YAML).
 */
@ConfigurationProperties(prefix = "cv-golden-master-template")
public record GoldenMasterCvTemplateProperties(String path) {
}
