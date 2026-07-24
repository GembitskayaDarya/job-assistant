ALTER TABLE vacancy_import_session
    ADD COLUMN vacancy_id UUID REFERENCES vacancy (id) ON DELETE SET NULL;

-- Nullable: every historical row from before this migration has vacancy_id = NULL, which is
-- exactly right for them (no session before this step ever completed with a linked vacancy).
-- It is populated exactly once, by the atomic Save transaction (WAITING_FOR_CONFIRMATION ->
-- COMPLETED, see completeIfWaitingForConfirmation), never updated afterwards.
--
-- ON DELETE SET NULL rather than CASCADE or RESTRICT: a completed import session is a Telegram
-- audit record in its own right (who imported what, when), independent of whether the vacancy
-- it produced still exists. Deleting the session along with its vacancy (CASCADE) would destroy
-- that history for no reason, and blocking vacancy deletion entirely (RESTRICT) would be too
-- rigid given vacancy deletion isn't even implemented yet. Nulling the reference keeps the
-- session record intact while gracefully dropping a now-dangling link.
CREATE INDEX idx_vacancy_import_session_vacancy_id
    ON vacancy_import_session (vacancy_id)
    WHERE vacancy_id IS NOT NULL;
