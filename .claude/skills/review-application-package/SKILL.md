---
name: review-application-package
description: >
  Manual/development QA for one generated application package (CV PDF + cover letter PDF) from
  this project's own PDFBox renderer - ATS-safe text ordering, canonical Technical Skills,
  fixed-content equality against the approved baseline, cover-letter provenance-leak check, and
  visual page inspection (clipping, overflow, separators touching text, page balance). Adapts the
  visual-QA rubric vendored from JobClaw's review-render skill (see
  .claude/skills/_vendor/jobclaw-skills/) to this project's actual PDFBox artifacts.
when_to_use: >
  After generating a real application package via the normal PrepareApplicationPackageUseCase flow
  (Telegram command, or a diagnostic bootRun runner), when you want a PASS/FAIL judgment on the
  resulting CV/cover-letter PDFs before trusting them - not a substitute for AtsCvVerifier (which
  still runs automatically as part of generation) but a second, human-eyes-style check this repo's
  automated tests cannot perform: does the page actually look right.
user-invocable: true
allowed-tools: Bash, Read
---

# review-application-package

This is a **development/manual QA skill only** - it never runs in production, is never called by
Java code, and does not gate `PrepareApplicationPackageUseCase`. It exists because Sprint 11's own
acceptance experience showed that green tests and a passing `AtsCvVerifier` are necessary but not
sufficient: real regressions (provenance leaks, duplicated content, a hidden rendering gap) were
only found by actually opening the PDF and reading it, or by running the real production flow
end-to-end against real data. This skill formalizes that inspection loop.

It complements, and never replaces, `AtsCvVerifier` (structural ATS-readability verification,
already wired into `RenderApplicationMaterialsUseCase`). You need **both**:

- **ATS structural verification** (automated, already running) - reading order, section presence,
  skill terms extractable.
- **Visual PDF QA** (this skill, manual) - what a recruiter's eyes actually see on the page.

## Inputs

- A `TailoredCvDocument` was rendered to a CV PDF and a cover letter PDF by the normal
  `PrepareApplicationPackageUseCase.prepare(vacancyId)` flow - locate the artifact files via
  `ApplicationMaterialArtifactRepositoryPort`/`FileStoragePort`, or ask the user for the exported
  file paths if they already pulled them out of Telegram.
- The vacancy this package was generated for (title/company at minimum), so Technical Skills
  relevance can be judged.

## Step 1 - Extract and read the text

Use the project's established pattern for inspecting rendered PDF text (a throwaway Python venv +
`pypdf`, never committed):

```bash
python3 -m venv /tmp/pdfqa-venv && /tmp/pdfqa-venv/bin/pip install -q pypdf
/tmp/pdfqa-venv/bin/python - <<'EOF'
from pypdf import PdfReader
for label, path in [("CV", "/path/to/cv.pdf"), ("Cover Letter", "/path/to/cover-letter.pdf")]:
    reader = PdfReader(path)
    print(f"=== {label}: {len(reader.pages)} page(s) ===")
    for i, page in enumerate(reader.pages, 1):
        print(f"--- page {i} ---")
        print(page.extract_text())
EOF
```

Delete the venv (`rm -rf /tmp/pdfqa-venv`) when done - it is scratch, never part of the repo.

## Step 2 - Content checks (read the extracted text)

1. **ATS-safe ordering** - header (name, headline, location, phone, email, LinkedIn text) before
   Professional Summary before Technical Skills before Professional Experience before Personal
   Projects before Education before Languages. This mirrors `AtsCvVerifier`'s own cursor order -
   if the real verifier already ran (check the generation's status/logs), you are confirming its
   result by eye, not re-deriving it from scratch.
2. **Technical Skills are canonical/high-level** - no narrow sub-variant should appear alongside
   its own canonical family (e.g. never both "Kafka" and "Kafka Partitions"; never both "Spring
   Boot" and "Spring Security"/"Spring MVC"/"Spring Data JPA"). Count the skills: target 8-10, hard
   ceiling 10 (see `CvSkillCanonicalizationPolicy`/`CvAssembler.MAX_TAILORED_SKILLS`). Flag any
   keyword-stuffed or redundant entry.
3. **Only Technical Skills differ from the approved baseline** - this project's CV product rule
   (Sprint 11 Final CV Policy) is "CV tailoring = Technical Skills only." Diff this CV's
   Professional Summary, career history text (company/position/project names, dates,
   responsibilities, achievements, technology lines), Mentoring, Personal Project, Education, and
   Languages against another package generated for a *different* vacancy (or against
   `config/private/baseline-cv-selection.yml` + `config/career-history.yml`'s approved content
   directly, without printing real personal data into chat unnecessarily). Any difference outside
   Technical Skills is a FAIL.
4. **Fixed content present and intact** - Professional Summary matches the approved fixed text
   verbatim; Mentoring is never empty; Personal Project is present with its factual GitHub URL;
   Education is present; Languages are present WITH proficiency (e.g. "English: Upper-Intermediate"),
   never bare language names.
5. **Cover letter provenance/debug leakage** - grep the extracted cover-letter text for `sourceIds`,
   `sourceRefs`, `EVIDENCE_`, and a raw UUID pattern (`grep -Eo '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'`).
   Any match is a FAIL - this is exactly the class of bug `GeneratedCoverLetterValidator`'s
   `PROVENANCE_LEAK_PATTERN` guards against in production; finding one here means that guard
   somehow didn't fire and is a genuine incident, not a cosmetic issue.
6. **Cover letter shape** - roughly 200-300 words, 3-4 short paragraphs, vacancy-specific, no
   generic "I am writing to express my interest" boilerplate, no invented company knowledge.

## Step 3 - Visual inspection (render page images and actually look)

PDFBox output has no built-in PNG export in this repo, so rasterize with `pdftoppm` (Poppler,
`brew install poppler` if missing) or `pdftocairo`:

```bash
pdftoppm -png -r 150 /path/to/cv.pdf /tmp/pdfqa-cv-page
```

**Read each resulting `/tmp/pdfqa-cv-page-*.png` with the Read tool** (it renders images) and judge
against this rubric - adapted from the vendored JobClaw `review-render` rubric
(`.claude/skills/_vendor/jobclaw-skills/review-render/reference/rubric.md`) to this renderer's own
known failure modes:

| Check | What a FAIL looks like |
|---|---|
| Clipping / overflow | Text cut off at a page edge or margin |
| Overlapping text | Two lines/blocks visually collide |
| Separator lines | A horizontal rule touches or crosses adjacent text (see `PdfPageCursor`'s `RULE_TO_CONTENT_GAP`/`HEADING_TO_RULE_GAP`) |
| Multi-word skills | A skill like "Spring Boot" or "REST API" breaks mid-word across a line wrap |
| Margins | Inconsistent left/right margins between sections |
| Page breaks | An orphaned section heading at the bottom of a page with its content stranded on the next; a role heading separated from its dates |
| Page density | Page 1 excessively dense while page 2 is mostly empty, or vice versa |
| URLs | LinkedIn/GitHub URLs render fully, not truncated/broken |
| Section spacing | Inconsistent vertical rhythm between sections (compare gaps by eye) |

Clean up the rasterized PNGs (`rm /tmp/pdfqa-cv-page-*.png`) once you're done looking at them -
scratch output, never committed.

## Step 4 - Verdict

Report **PASS** or **FAIL** with concrete reasons, structured as:

```
CV:      PASS/FAIL - <page count>, ATS order <ok/violation>, skills=<list>, fixed-content <equal/diff:...>
Cover:   PASS/FAIL - <word count>, provenance <clean/leak:...>
Visual:  PASS/FAIL - <issue list, or "none found">
```

Never accept a package solely because the automated Java test suite is green - this skill's whole
purpose is to catch what tests cannot see. If you find a FAIL, report it plainly; do not soften a
real defect into a "minor note" to make the package look more finished than it is.
