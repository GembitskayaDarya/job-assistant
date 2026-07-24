package com.darya.jobassistant.vacancyimport.model;

import com.darya.jobassistant.vacancyimport.exception.VacancyImportSessionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * A guided, persistent Telegram vacancy-import workflow for one chat+user pair. Plain domain
 * object (no framework or Telegram SDK types) - it protects its own state transitions, so an
 * invalid transition can only ever be attempted through its own methods, never by an external
 * caller flipping a field directly.
 */
@Getter
public class VacancyImportSession {

    private static final int MIN_DESCRIPTION_LENGTH = 20;

    /**
     * States from which a session can be cancelled or can expire. Deliberately narrower than
     * {@link ImportState#activeStates()}: {@link ImportState#EXTRACTING} is "in flight" for
     * uniqueness purposes, but per the state machine it is a transient, automated-processing
     * state that can only resolve to {@code WAITING_FOR_CONFIRMATION} or {@code FAILED} - it
     * has no direct path to {@code CANCELLED} or {@code EXPIRED}.
     */
    private static final Set<ImportState> USER_INTERACTION_STATES = EnumSet.of(
            ImportState.WAITING_FOR_URL, ImportState.WAITING_FOR_DESCRIPTION, ImportState.WAITING_FOR_CONFIRMATION);

    private final UUID id;
    private final long telegramChatId;
    private final long telegramUserId;
    private ImportState state;
    private String sourceUrl;
    private String rawDescription;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Instant expiresAt;

    /**
     * True only for an instance minted by {@link #start}; false for one rehydrated via
     * {@link #reconstruct}. Exists purely so {@link VacancyImportSessionMapper} can tell Spring
     * Data JPA whether a save is a first-ever insert or an update: this session carries a
     * client-generated id (required so a session has an id before it's ever persisted), so the
     * usual "id is null => new" heuristic Spring Data uses for every other entity in this project
     * doesn't work here - without this flag, {@code save()} would try to UPDATE a row that
     * doesn't exist yet and Hibernate would report a stale-state error.
     */
    @Getter(AccessLevel.NONE)
    private final boolean newSession;

    private VacancyImportSession(
            UUID id,
            long telegramChatId,
            long telegramUserId,
            ImportState state,
            String sourceUrl,
            String rawDescription,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            boolean newSession) {
        this.id = id;
        this.telegramChatId = telegramChatId;
        this.telegramUserId = telegramUserId;
        this.state = state;
        this.sourceUrl = sourceUrl;
        this.rawDescription = rawDescription;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
        this.newSession = newSession;
    }

    /**
     * Starts a new import session for the given Telegram chat+user, in {@code WAITING_FOR_URL}.
     * {@code clock} is the sole time source (no direct {@code Instant.now()} calls), so domain
     * tests can supply a fixed clock and stay deterministic.
     */
    public static VacancyImportSession start(long telegramChatId, long telegramUserId, Clock clock, Duration timeToLive) {
        Instant now = Instant.now(clock);
        return new VacancyImportSession(
                UUID.randomUUID(),
                telegramChatId,
                telegramUserId,
                ImportState.WAITING_FOR_URL,
                null,
                null,
                now,
                now,
                now.plus(timeToLive),
                true);
    }

    /**
     * Rehydrates a session from already-validated persisted state, bypassing the transition
     * methods. Package-private and only meant to be called by {@link VacancyImportSessionMapper},
     * which lives in this same package for exactly that reason.
     */
    static VacancyImportSession reconstruct(
            UUID id,
            long telegramChatId,
            long telegramUserId,
            ImportState state,
            String sourceUrl,
            String rawDescription,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {
        return new VacancyImportSession(
                id, telegramChatId, telegramUserId, state, sourceUrl, rawDescription, createdAt, updatedAt, expiresAt, false);
    }

    /** Package-private: only {@link VacancyImportSessionMapper} needs this. */
    boolean isNewSession() {
        return newSession;
    }

    public void provideUrl(String url, Clock clock) {
        requireState(ImportState.WAITING_FOR_URL);
        this.sourceUrl = validateUrl(url);
        this.state = ImportState.WAITING_FOR_DESCRIPTION;
        touch(clock);
    }

    public void provideDescriptionAndStartExtraction(String description, Clock clock) {
        requireState(ImportState.WAITING_FOR_DESCRIPTION);
        this.rawDescription = validateDescription(description);
        this.state = ImportState.EXTRACTING;
        touch(clock);
    }

    public void markExtractionSucceeded(Clock clock) {
        requireState(ImportState.EXTRACTING);
        this.state = ImportState.WAITING_FOR_CONFIRMATION;
        touch(clock);
    }

    /** Returns to {@code WAITING_FOR_DESCRIPTION}, discarding the previous raw description. */
    public void retryDescription(Clock clock) {
        requireState(ImportState.WAITING_FOR_CONFIRMATION);
        this.rawDescription = null;
        this.state = ImportState.WAITING_FOR_DESCRIPTION;
        touch(clock);
    }

    public void complete(Clock clock) {
        requireState(ImportState.WAITING_FOR_CONFIRMATION);
        this.state = ImportState.COMPLETED;
        touch(clock);
    }

    public void cancel(Clock clock) {
        if (!USER_INTERACTION_STATES.contains(state)) {
            throw new VacancyImportSessionException("Cannot cancel a session in state " + state);
        }
        this.state = ImportState.CANCELLED;
        touch(clock);
    }

    public void fail(Clock clock) {
        requireState(ImportState.EXTRACTING);
        this.state = ImportState.FAILED;
        touch(clock);
    }

    public void expire(Clock clock) {
        if (!USER_INTERACTION_STATES.contains(state)) {
            throw new VacancyImportSessionException("Cannot expire a session in state " + state);
        }
        Instant now = Instant.now(clock);
        if (now.isBefore(expiresAt)) {
            throw new VacancyImportSessionException("Session has not yet reached its expiration time");
        }
        this.state = ImportState.EXPIRED;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return state.isActive();
    }

    private void requireState(ImportState expected) {
        if (state != expected) {
            throw new VacancyImportSessionException("Cannot perform this operation while the session is in state " + state);
        }
    }

    private void touch(Clock clock) {
        this.updatedAt = Instant.now(clock);
    }

    private static String validateUrl(String url) {
        if (url == null) {
            throw new VacancyImportSessionException("Source URL must not be null");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new VacancyImportSessionException("Source URL is not a valid URI: " + url);
        }
        String scheme = uri.getScheme();
        boolean isHttpOrHttps = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!uri.isAbsolute() || !isHttpOrHttps) {
            throw new VacancyImportSessionException("Source URL must be an absolute HTTP or HTTPS URL: " + url);
        }
        return url;
    }

    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new VacancyImportSessionException("Vacancy description must not be blank");
        }
        if (description.trim().length() < MIN_DESCRIPTION_LENGTH) {
            throw new VacancyImportSessionException("Vacancy description is too short to be meaningful");
        }
        return description;
    }
}
