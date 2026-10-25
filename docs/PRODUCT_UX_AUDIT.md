# Product UI/UX Audit — JLU Mail

**Scope:** source-based review of the current Android application (Kotlin + Jetpack Compose), based on `main` at the time of this audit. No user research, device screenshots, production analytics, or physical-device testing were available. Findings below are therefore marked as **Observed** (visible in source) or **Hypothesis** (requires usability testing).

## Executive summary

The product already contains valuable mailbox, filtering, translation, safety, task, calendar, and notes capabilities. The main problem is that the UI exposes too many of those capabilities at once, while a few labels imply more intelligence than the implementation actually provides. The first pass should make reading the inbox simpler, make summaries honest and useful, and preserve email paragraph/list structure.

## Prioritized findings

| Priority | Evidence | User impact | Recommended change |
|---|---|---|---|
| **High — trust** | `MailQuickSummaryBuilder` explicitly uses deterministic extraction: it takes at most two sentences and truncates at 420 characters. The UI calls it “Quick read.” There is no generative LLM client in the current source tree. | Users can interpret a short extract as an AI-written summary and lose confidence when it misses context. | Call these “Mail notes” or “Extractive notes,” include relevant source sentences plus detected dates/times/amounts/actions, and state which method/model is actually used. Do not claim generative AI. |
| **High — discoverability** | `MailScreen` permanently places the app-session line, folder chips, protocol/sync status, and a daily/weekly card before the list. Filters and export controls are in the scrolling list. | Inbox content starts lower on the screen and actions are split between the header and list. | Collapse the digest by default, keep the mailbox status compact, make period selection visible within the digest, and keep the mail list as the primary content. |
| **High — digest scope** | `MailBriefingBuilder` gives only today/this-week counts and up to three possible-action links; it does not produce period-specific notes. | Users cannot scan what changed in their mailbox over a chosen number of days. | Add a 1/3/7/14/30-day rolling view with message-level, source-grounded notes and action/date clues. Label its extraction method clearly. |
| **High — readability** | `MailTextFormatter.plainText` collapses all whitespace, including paragraph breaks. The translated body uses `readableBody`, but the original German body shown alongside it uses `plainText`. | Long institutional email, lists, deadlines, and translated paragraphs become difficult to scan or compare. | Use a paragraph-aware body formatter for both languages; normalize HTML line breaks, list items, non-breaking spaces, and excessive blank lines. |
| **Medium — detail-screen density** | `EmailDetailSheet` may stack safety status, attachments, quick notes, thread context, follow-up, action suggestions, detected changes, translation controls, and the body. | The primary task—understanding and replying to a message—can be pushed down by secondary information. | Prioritize subject/sender/body and essential actions; make secondary analysis sections collapsible or progressively disclosed. This needs screenshot/device validation before broader changes. |
| **Medium — navigation** | Bottom navigation exposes six destinations, including “EWS Proof,” alongside five user-facing work areas. | On smaller screens, labels may compete and diagnostics may be more prominent than everyday tasks. | Test whether diagnostics belongs under Settings/Help or remains a primary destination; do not move it without task-frequency evidence. |
| **Polish — consistency** | Some screens use multiple colored cards and repeated boxed sections; labels such as “Quick read,” “Daily & weekly briefing,” and “EWS Proof” use different terminology styles. | The interface can feel more like a feature inventory than one coherent product. | Use a small type scale, consistent spacing, restrained surface variants, descriptive labels, and one clear primary action per region. |

## Key user journeys

1. **Find an important mail:** open Mail → choose folder → scan unread/priority → open mail → verify sender/content → reply or create task. Keep the inbox list prominent, expose filters clearly, and ensure all suggested urgency is described as heuristic rather than certain.
2. **Understand a long German message:** open mail → review source-grounded notes → translate → compare translated and original text. Notes must not replace the source; preserve headings, paragraphs, lists, dates, and amounts in both views.
3. **Review a recent period:** open Mail → choose 1/3/7/14/30 days → scan recent notes/action cues → open an original message. Make the range explicit and show how many messages are represented.
4. **Export selected messages:** filter → select messages → choose Markdown/CSV/TXT → save. Keep attachment limitations visible: the current model stores attachment references, not original binary data.

## Recommended information architecture

- **Mail:** compact mailbox/folder selector; compact sync state; collapsed “Mail notes” preview with a period selector; message list with search/filter/selection.
- **Message detail:** subject + sender + date; concise safety warning only when relevant; readable source body; translated body with clear language labels; action buttons. Secondary material (thread context, inferred tasks, extraction notes) should be visually subordinate.
- **Notes:** retain user-created notes as first-class content. Distinguish a note from a generated/extracted mail digest; show source mail and generation method.
- **Settings/Diagnostics:** keep protocol diagnostics available without letting technical status dominate the everyday reading flow.

## Design direction

- Build on the existing JLU navy/gold palette; do not add gradients or decorative shadows.
- Use consistent 8/12/16/24 dp spacing, clear headings, readable body line height, and muted metadata.
- Use chips for short mutually-exclusive filters, an explicit reset state, and predictable selected/unselected states.
- Every extraction/translation view should indicate original vs translated/source text and provide an easy return to the original.
- Show empty, loading, offline/model-download, partial translation, and failure states with clear next steps.
- Keep destructive and irreversible actions separate from primary reply/read actions.

## AI / model disclosure

At this audit, the source includes **Google ML Kit Translate** for offline machine translation, with a phrase-based fallback. Mail summaries and triage are deterministic local extractors/classifiers; no generative AI provider/model or LLM request is wired into the app. Do not tell users that an LLM wrote their notes. A genuine generative summary would require an explicitly selected model/provider, privacy and credential handling, a bounded prompt, source-grounding, model failure handling, and tests. Any future model prompt should prohibit inventing facts and require unknown values to be marked “not stated.”

## Validation / PDCA

### Plan
- Reduce above-the-fold clutter without removing existing mailbox capabilities.
- Improve source-body formatting and create useful notes for a selected rolling day range.
- Be explicit about rules-based extraction vs ML Kit translation vs generative AI.

### Do
- Implement the first focused pass in the companion PR: collapsed period-based notes, longer source-grounded extractive notes, and readable body formatting.

### Check
- CI: unit tests, lint, and debug APK build must pass.
- Unit tests: day-window filtering, folder exclusion, action/unread counts, note selection limit, paragraph preservation, and key-fact extraction.
- Manual/device checks still required: small-screen layout, accessibility font scaling, screen reader labels, actual German messages with HTML lists/tables, translation model download/offline behavior, and Room migration on an existing install.

### Act
- Fix any CI or device regressions before merging.
- Next iteration: collapse secondary detail sections, verify accessibility and navigation choices, and only add generative AI after a provider/model and privacy policy are selected.

**Not measured:** no completion-time, click-count, satisfaction, accessibility-audit, or device-rendering improvement is claimed until tested.
