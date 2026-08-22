package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sprint 11 Golden Master Template Rendering: {@link GoldenMasterCvTemplate} must fail application
 * startup loudly (never silently fall back to a different renderer) when the configured template
 * file is missing or structurally invalid, and must otherwise load/hash/integrity-verify it exactly
 * once. Uses {@link GoldenMasterFixture} - see that class's javadoc for why no real private CV data
 * is needed here.
 */
class GoldenMasterCvTemplateTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_missingFile_throwsImmediately() {
        GoldenMasterCvTemplateProperties properties = new GoldenMasterCvTemplateProperties(tempDir.resolve("does-not-exist.pdf").toString());

        assertThatThrownBy(() -> new GoldenMasterCvTemplate(properties))
                .isInstanceOf(GoldenMasterCvTemplateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void constructor_validTemplate_loadsHashesAndPassesIntegrityCheck() throws IOException {
        GoldenMasterCvTemplate template = validTemplate();

        assertThat(template.getSha256()).hasSize(64).matches("[0-9a-f]+");
        template.verifyIntegrity();

        try (PDDocument document = template.freshDocument()) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void freshDocument_returnsAnIndependentMutableCopy_everyCall() throws IOException {
        GoldenMasterCvTemplate template = validTemplate();

        try (PDDocument first = template.freshDocument(); PDDocument second = template.freshDocument()) {
            assertThat(first).isNotSameAs(second);
            assertThat(first.getNumberOfPages()).isEqualTo(second.getNumberOfPages());
        }
    }

    @Test
    void sha256_reflectsTheExactFileContent_changesWhenTheFileChanges() throws IOException {
        byte[] fixtureBytes = GoldenMasterFixture.build();
        Path path = tempDir.resolve("golden-master-fixture.pdf");
        Files.write(path, fixtureBytes);
        GoldenMasterCvTemplate template = new GoldenMasterCvTemplate(new GoldenMasterCvTemplateProperties(path.toString()));

        assertThat(template.getSha256()).isEqualTo(sha256Hex(fixtureBytes));
        assertThat(template.getSha256()).isNotEqualTo(sha256Hex(GoldenMasterFixture.buildMissingLanguagesHeading()));
    }

    @Test
    void verifyIntegrity_missingSectionHeading_throws() throws IOException {
        Path path = tempDir.resolve("broken-golden-master.pdf");
        Files.write(path, GoldenMasterFixture.buildMissingLanguagesHeading());
        GoldenMasterCvTemplate template = new GoldenMasterCvTemplate(new GoldenMasterCvTemplateProperties(path.toString()));

        assertThatThrownBy(template::verifyIntegrity)
                .isInstanceOf(GoldenMasterCvTemplateException.class)
                .hasMessageContaining("LANGUAGES");
    }

    private GoldenMasterCvTemplate validTemplate() throws IOException {
        Path path = tempDir.resolve("golden-master-fixture.pdf");
        Files.write(path, GoldenMasterFixture.build());
        return new GoldenMasterCvTemplate(new GoldenMasterCvTemplateProperties(path.toString()));
    }

    private String sha256Hex(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
