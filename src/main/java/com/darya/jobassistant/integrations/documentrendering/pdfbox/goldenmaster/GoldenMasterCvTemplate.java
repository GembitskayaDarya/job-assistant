package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import com.darya.jobassistant.applicationmaterials.render.model.CvSectionHeadings;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Sprint 11 Golden Master Template Rendering: loads the single, private, approved reference CV PDF
 * once at startup - {@code cv-golden-master-template.path}, gitignored under {@code config/private/}
 * (same convention as {@code baseline-cv-selection.yml}) and mounted read-only into the container
 * (see {@code docker-compose.yml}) - and fails application startup loudly (never a silent fallback to
 * a different renderer) if the file is missing, unreadable, or does not have the exact structure
 * {@link TechnicalSkillsRegionLocator} requires.
 *
 * <p>The golden master PDF <strong>is</strong> the approved CV template: every section other than
 * Technical Skills is drawn exactly as this file already draws it - see {@link GoldenMasterCvRenderer}.
 * This class only loads, hashes, and structurally validates the template; it never decides what
 * skills to render.
 *
 * <h2>Statelessness</h2>
 *
 * The raw bytes are read and validated exactly once, at construction. {@link #freshDocument()}
 * returns a brand-new {@link PDDocument} parsed from those same immutable bytes on every call - the
 * caller mutates and closes its own copy per render, so concurrent renders never share or corrupt a
 * {@link PDDocument} instance.
 *
 * <h2>Version identity</h2>
 *
 * {@link #sha256()} is a deterministic hash of the template bytes - a template change (a new
 * approved CV file) changes this hash even though nothing in the application's own code changed.
 * This is exposed for logging/diagnostics; cache/artifact invalidation itself is already handled by
 * {@code RenderApplicationMaterialsUseCase#RENDERER_VERSION} (bumped whenever this rendering
 * architecture or template changes), the same existing mechanism the rest of the render pipeline
 * already uses to force a fresh render without requiring a new AI generation.
 */
@Component
@Slf4j
public class GoldenMasterCvTemplate {

    private final byte[] templateBytes;

    @Getter
    private final String sha256;

    public GoldenMasterCvTemplate(GoldenMasterCvTemplateProperties properties) {
        Path path = Path.of(properties.path());
        if (!Files.isRegularFile(path)) {
            throw new GoldenMasterCvTemplateException(
                    "Golden master CV template not found at configured path '" + path + "' - see "
                            + "cv-golden-master-template.path (CV_GOLDEN_MASTER_TEMPLATE_PATH)");
        }
        try {
            this.templateBytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new GoldenMasterCvTemplateException("Failed to read golden master CV template at '" + path + "'", e);
        }
        this.sha256 = sha256Hex(templateBytes);
        log.info("Loaded golden master CV template from '{}' ({} bytes, sha256={})", path, templateBytes.length, sha256);
    }

    @PostConstruct
    void verifyIntegrity() {
        try (PDDocument document = freshDocument()) {
            if (document.getNumberOfPages() != 2) {
                throw new GoldenMasterCvTemplateException(
                        "Golden master CV template must have exactly 2 pages, has " + document.getNumberOfPages());
            }
            String fullText = new PDFTextStripper().getText(document);
            for (String heading : new String[]{
                    CvSectionHeadings.PROFESSIONAL_SUMMARY, CvSectionHeadings.TECHNICAL_SKILLS, CvSectionHeadings.PROFESSIONAL_EXPERIENCE,
                    CvSectionHeadings.MENTORING_EXPERIENCE, CvSectionHeadings.PERSONAL_PROJECT, CvSectionHeadings.EDUCATION, CvSectionHeadings.LANGUAGES}) {
                if (!fullText.contains(heading)) {
                    throw new GoldenMasterCvTemplateException("Golden master CV template is missing expected section heading '" + heading + "'");
                }
            }
            // A successful return proves the Technical Skills region resolves uniquely and
            // unambiguously - see TechnicalSkillsRegionLocator's javadoc.
            TechnicalSkillsRegionLocator.locate(document);
        } catch (IOException e) {
            throw new GoldenMasterCvTemplateException("Failed to parse golden master CV template for integrity verification", e);
        }
        log.info("Golden master CV template integrity verified (2 pages, all section headings present, Technical Skills region resolved uniquely)");
    }

    /** A fresh, independently mutable/closeable copy - see class javadoc. */
    PDDocument freshDocument() throws IOException {
        return Loader.loadPDF(templateBytes);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must always be available", e);
        }
    }
}
