-- Guided Telegram import already extracts location, remote work mode and free-text salary
-- (see vacancy_import_draft), but the vacancy table had no columns to carry them into the
-- persisted Vacancy, so they were silently dropped on Save. These columns are nullable because
-- existing rows and other ingestion paths (e.g. RemoteOK, which uses salary_min/salary_max
-- instead) may not populate them.
ALTER TABLE vacancy
    ADD COLUMN location    VARCHAR(300),
    ADD COLUMN remote_mode VARCHAR(20),
    ADD COLUMN salary_text VARCHAR(200);
