# JLU Mail UI/UX Audit and PDCA Plan

Date: 2026-10-26
Repository: `zixwixiz/Mail`
Scope: Existing Android Jetpack Compose app, inspected from source. No screenshot-based visual review or device usability study has been performed; findings below are source-grounded hypotheses until tested on a device.

## Executive summary

The app already has substantial workflows (mail folders, category/date/sender filters, multi-select export, reading triage, mail translation, notes, calendar, tasks, contacts, and security checks). The main risk is not a lack of features: useful actions compete for attention, and the “smart” summary experience is too shallow to justify its placement. Improve content quality and hierarchy before attempting a wholesale visual rewrite.

## Prioritized findings

| Priority | Finding and evidence | Why it matters | Recommended fix | Validation |
|---|---|---|---|---|
| Critical | `MailQuickSummaryBuilder` collapses message whitespace and uses at most two sentences / 420 characters. The current branch expands this to up to eight relevance-picked sentences (maximum 2,000 characters) and separates key details. It is explicitly extractive, not a generative AI summary. | Important context and instructions later in long administrative emails can be missed; users may overestimate the feature. | Make an expanded, structured note that preserves paragraphs and extracts dates, times, amounts, actions and attachments; label the on-device, rules-based nature honestly until a real LLM is configured. | Unit test long multi-paragraph mail; compare extracted fields with source; ensure no claim of measured AI accuracy. |
| High | `MailScreen` renders folder chips, protocol/sync status, a daily/weekly briefing, sender/category/date filters, selection/export, a reading queue, and the message list within the same screen. | Large above-the-fold overhead and competing cards reduce the space available for the primary task: reading mail. | Keep folders and core search/filter affordances easy to reach; collapse advanced selection/export and secondary summaries behind explicit controls; keep filter state visible. | Device test on compact and large Android screens; measure scroll-to-first-message and task completion. |
| High | `EmailDetailSheet` can stack quick summary, thread context, follow-up, safety findings, detected changes, smart actions, translation controls, and message content in a single scrolling sheet. | The important content can be far down the sheet and the screen becomes hard to scan. | Use clear section titles, consistent spacing, one primary reading/translation path, and collapsible secondary analysis. Keep safety alerts visible and avoid hiding them behind decoration. | Test reading, reply, translate, create task, add calendar event and close flows. |
| High | Translated body display uses `readableBody`, but the “Original German Content” path uses `plainText`, which collapses whitespace. `TranslationService` translates whole strings rather than paragraph-by-paragraph. | Newlines, lists and paragraph relationships may be lost or flattened in translated mail. | Preserve source paragraph boundaries, translate paragraphs separately, rejoin with the original separators, and use the readable body formatter for both translation and original views. | Unit tests for plain text, HTML paragraphs, lists, blank lines and bilingual comparison. |
| High | Inbox briefing provides today/week counts and up to three possible actions but no detailed user-selected date-range digest. | Users cannot get a longer review note for the last 2/3/5/10 days. | Add “Mail notes” for a chosen period, with message count, key themes/categories, dates/times, possible actions and source-message references. Avoid inventing decisions or summarizing mail outside the selected period. | Fixed-time unit tests, date-window tests, empty states and source traceability checks. |
| Medium | Labels such as “Smart reading queue” and “Quick read” may sound like AI even where code is explicitly rules-based or extractive. | Unclear capability claims damage trust. | Show “On-device, rules-based” or “Extracted from message text” provenance; reserve “AI-generated” for a real configured generative model. | Verify explanatory label is present in UI and app settings/help. |
| Medium | Some message-detail sections repeat similar card treatment. | Important actions and content have weak visual hierarchy when everything looks equally prominent. | Establish a consistent hierarchy for title/meta, primary action, message body, summary, and secondary insights; use existing Material theme tokens. | Compose previews/screenshots and accessibility review. |
| Polish | Export body originally used `plainText` for body content. | Exported notes can lose line/paragraph breaks too. | Use readable formatting for Markdown/TXT; document CSV cell newline handling. | Source now uses `readableBody`; export-format regression test and user-device check remain required. |

## Important journeys to validate

1. **Open and understand a new administrative email:** inbox → message → long summary/notes → verify source → translate → reply or create task.
2. **Review a recent period:** inbox → choose 1/2/3/5/10/30-day window → generate notes → open a cited source message → export if needed.
3. **Find and export messages:** filter by sender/category/date → select messages → choose format → save document.
4. **Turn an event email into a calendar item:** message → review extracted date/time/time zone → correct it if needed → add to calendar.
5. **Read translated email:** translate → inspect formatted English body and original side-by-side → switch languages/clear translation.

## Proposed information architecture

- **Inbox:** folders, search, a compact count/status row, a single clear message list, and filters accessible without a large stack of dashboards.
- **Filters / selection:** sender, category and date together; export controls appear only after selection is enabled.
- **Mail notes:** selected time period, transparent processing description, high-level overview, action/date/time sections, category breakdown, and source mail links.
- **Message detail:** subject and sender, primary message body / translation toggle, actionable insights (deadlines/actions), secondary safety/thread context, then lower-priority details.
- **Notes:** original note first, translation below it; show translation status/error and never overwrite the source note.

## Design principles

- Preserve existing account, EWS, sync, database and calendar business logic.
- Use existing Material 3 theme and components; do not add decorative gradients or arbitrary shadows.
- Make primary actions obvious and secondary analysis collapsible where possible.
- Maintain accessible touch targets, content descriptions, color contrast, scalable text, and proper empty/loading/error states.
- Clearly distinguish source facts from rule-based inferences.
- Do not claim usability improvement is measured without a real user test.

## PDCA checklist

### Plan
- [x] Inspect key screen, summary, translation, theme, and CI sources.
- [x] Rank the main source-supported problems.
- [ ] Validate assumptions with screenshots/device testing.
- [ ] Agree on one coherent visual hierarchy after observing the running app.

### Do
- [x] Replace the short extractive overview with longer structured notes (local extraction; not generative AI).
- [x] Add date-range mail notes for recent messages (1, 2, 3, 5, 10, and 30 days).
- [x] Preserve paragraphs/lists for original and translated text in the source formatter and translation path.
- [x] Reduce inbox overhead by making the detailed mail-notes panel and today's action list collapsible. [ ] Further collapse lower-priority detail-sheet analysis after device review.
- [ ] Keep schedule-from-PDF/text import as a distinct follow-up task.

### Check
- [ ] Run unit tests and lint (recent attempts exposed test failures; do not mark complete until a current green run is confirmed).
- [ ] Build the debug APK and inspect the generated artifact.
- [ ] Smoke-test existing mailbox, translation, export, task, and calendar flows on a device.
- [ ] Confirm Room migration and empty/error states.

### Act
- [x] Update stale quick-summary assertions and specify a supported Robolectric SDK after CI exposed these issues. [ ] Confirm a current green run and address any further failures.
- [ ] Revisit priority order based on device findings.
- [ ] Release only after CI and device verification; do not equate a successful build with a validated UX.

## AI / translation provenance

At the audited commit, the quick mail summary is deterministic and extractive; it is **not** generated by a large language model. Translation uses Google ML Kit Translate with on-device models when available, and phrase-based fallback rules on failure. No configured generative LLM/API is present in the inspected summary path. Do not describe the summary as “AI-generated” unless a real model/provider is added and its privacy, API key handling, cost, and failure behavior are designed explicitly.
