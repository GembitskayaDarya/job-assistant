-- Test-only fixture (src/test/resources/db/testdata) - never on the production classpath. Seeds
-- a valid "primary" Candidate Profile so AbstractIntegrationTest subclasses' full application
-- context can pass Sprint 9 Step 4's fail-fast CandidateProfileStartupValidator without each test
-- inserting one itself. Only applied when spring.flyway.locations explicitly includes
-- classpath:db/testdata (see AbstractIntegrationTest) - never picked up by @DataJpaTest/slice
-- tests using the default classpath:db/migration location alone.
INSERT INTO candidate_profile (profile_key, target_role, seniority, experience_years, current_country, relocation_allowed)
VALUES ('primary', 'Senior Java Backend Engineer', 'Senior', 6, 'Poland', false);

INSERT INTO candidate_profile_skill (candidate_profile_id, skill_name, proficiency)
SELECT id, 'Java', 'EXPERT' FROM candidate_profile WHERE profile_key = 'primary'
UNION ALL
SELECT id, 'Spring Boot', 'STRONG' FROM candidate_profile WHERE profile_key = 'primary'
UNION ALL
SELECT id, 'PostgreSQL', 'STRONG' FROM candidate_profile WHERE profile_key = 'primary';

INSERT INTO candidate_profile_language (candidate_profile_id, language_code)
SELECT id, 'en' FROM candidate_profile WHERE profile_key = 'primary';
