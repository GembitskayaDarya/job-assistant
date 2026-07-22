CREATE UNIQUE INDEX uk_vacancy_url ON vacancy (url) WHERE url IS NOT NULL;
