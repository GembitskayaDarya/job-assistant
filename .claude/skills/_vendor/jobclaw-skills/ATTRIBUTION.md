# JobClaw — vendored reference material

Source repository: https://github.com/jain777/jobclaw-skills
Vendored commit: `68211e04d37044c49c3a91f3a29ce9647fada95f` (2026-06-09, tagged `v0.2.1` in the
upstream `plugin.json`)
License: MIT, © 2026 Jeevesh Jain — full text preserved in [`LICENSE.upstream`](LICENSE.upstream).

## What this is

This directory is a **reference-only** copy of a handful of JobClaw's Claude Code skill docs. It
exists so a developer (or Claude Code, when asked) can consult JobClaw's resume-rendering, visual
QA, cover-letter, and ATS-scoring conventions while working on this repo's own application-package
review tooling. Each vendored skill's main doc is named `REFERENCE.md`, not `SKILL.md` — a
deliberate rename from upstream so nothing here can ever be picked up by Claude Code's skill
discovery, even incidentally: nothing in this directory is invocable as a slash command, nothing
here runs automatically, and nothing in `src/main/java` depends on it. (Internal cross-reference
links inside the vendored docs, e.g. `../review-render/SKILL.md`, still say `SKILL.md` — that is
upstream's own original text, left unmodified rather than rewritten; it does not affect discovery
since these files are not under a bare `.claude/skills/<name>/` path in the first place.) See
"Sprint 11 — Final CV Policy + JobClaw Skill Integration" for the task that added it.

This repo's own production-facing skill —
[`.claude/skills/review-application-package/SKILL.md`](../review-application-package/SKILL.md) —
is a separate, original document written for this project. It *adapts ideas* from the rubric below
to our actual PDFBox-rendered CV/cover-letter PDFs; it does not extend or require JobClaw's Python
tooling (rendercv/Typst), which this repo never installs.

## What was vendored

| File | Why |
|---|---|
| `render-resume/REFERENCE.md` | ATS-safe rendering conventions, link-handling philosophy, page-density/page-count self-QA gate — reference for reasoning about our own PDFBox renderer's behavior. |
| `review-render/REFERENCE.md` + `review-render/reference/rubric.md` | The visual-QA loop and rubric (widows, page fill, overflow, alignment, hierarchy, readability floor, links/contact) — the direct model for this repo's own visual-QA workflow (Part 20/21). |
| `write-cover-letter/REFERENCE.md` | Style/length/structure conventions (word count, paragraph shape, anti-patterns like generic openers) used as a review checklist for our AI-generated cover letters. |
| `score-fit/REFERENCE.md` + `score-fit/reference/score-schema.md` | Requirement/keyword-matching methodology — reference for how vacancy requirements map to a Technical Skills selection. |
| `_shared/RULES.md` | The cross-skill "never fabricate" / no-echo-of-internal-context canon that the four skills above each reference — kept because those four files link to it. |
| `LICENSE.upstream` | Required by MIT attribution terms. |

## What was intentionally NOT installed

- **`skills/tailor-resume/`, `skills/apply-to-job/`** — these are the two workflows the task
  explicitly forbids from overriding this project's CV product policy (Sprint 11 Part 1 header).
  This project's CV tailoring is Technical-Skills-only and lives entirely in the Java codebase.
- **`skills/find-jobs/`, `research-company/`, `prep-interview/`, `mock-interview/`,
  `coach-negotiation/`, `career-coach/`, `map-career-path/`, `write-outreach/`,
  `answer-application-questions/`, `triage-inbox/`, `infer-status/`, `draft-reply/`,
  `request-human-input/`, `build-profile/`** — unrelated discovery/outreach/interview-prep/inbox
  workflows, not required by any of the four skills above.
- **`render-resume/scripts/`, `render-resume/templates/`** and `review-render`'s reliance on
  `--emit-png` — these call `rendercv` (Typst) against JobClaw's own `resumes/<slug>.json` /
  `.tailor.json` sidecar format. This repo's production renderer is the existing PDFBox Java
  renderer (`PdfBoxApplicationMaterialDocumentRenderer`) and stays that way; we did not install
  rendercv or any Python rendering runtime anywhere in this repo.
- **`knowledge/`, `profile/`, `tests/`, `docs/`, `.cursor/`, `.env.example`,
  `.claude-plugin/marketplace.json`, `.claude-plugin/plugin.json`** — JobClaw's own region packs,
  example master-profile (would otherwise read as fake "real" candidate data — explicitly
  disallowed by this task), test fixtures, and plugin-marketplace registration. This repo is not
  registered as a JobClaw plugin/marketplace entry; only the specific files above were copied.

## Nothing private was committed alongside this

No candidate data, API tokens, or generated CV/cover-letter PDFs were copied into this directory —
it contains only JobClaw's own upstream documentation files, unmodified except for being copied out
of their original directory tree.
