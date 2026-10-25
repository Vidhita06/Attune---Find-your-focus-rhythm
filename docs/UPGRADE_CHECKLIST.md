# Upgrade checklist (AI, personalization, reports, redesign)

Legend:
- ✅ implemented and connected to real data, with automated tests where marked
- 🟡 implemented, but not verified on a device
- ❌ not done

| Phase | Item | Status |
|---|---|---|
| 0 | Audit; backup (commit `f5cb7a4` pushed to `origin/clean-main`; work done on local branch `ai-upgrade`) | ✅ |
| 1 | Focus-aware routing (held / let through / review), with rules → safety → model priority | ✅ tests 🟡 device |
| 1 | Paused or no session → nothing held; outside-focus behaviour configurable; summary after pause or end | ✅ 🟡 device |
| 1 | Android limits explained in the UI (Focus status card, Rules, AI Mode, Privacy) | ✅ |
| 2 | Separate signals: importance, urgency, distraction, action, link, confidence; LOW / MEDIUM / HIGH / UNKNOWN risk | ✅ |
| 2 | Before You Open: reasons, rule override, Open / Delay / Dismiss / Mark important / Correct it (all persisted) | ✅ |
| 2 | Uncertain, short or link-only messages → kept visible for review, never silently held | ✅ tests |
| 3 | Reproducible synthetic dataset (3,105 rows, 266 templates, English and Hinglish, hard cases), group split | ✅ |
| 3 | Training, calibration and thresholds on validation; test metrics against majority, keyword and v1-rule baselines | ✅ `ml/reports` |
| 3 | Kotlin inference with golden-vector parity test; fallback to rules if the model is missing or invalid | ✅ tests |
| 4 | AI Mode toggle that really switches the decision path; playground; evaluation report; latency measured on the phone | ✅ 🟡 phone latency not yet measured |
| 4 | On-device LLM | ❌ judged impractical (size, memory, offline dependencies); documented |
| 5 | Local link analysis, never fetched; sensitive previews masked; no content in logs | ✅ |
| 6 | Personal Attention Profile (A–F) from real records, with empty states | ✅ tests |
| 7 | Daily / weekly / monthly reports with 8 charts, tap-to-inspect, like-for-like comparison, share (counts only) | ✅ tests |
| 8 | Feedback personalization (capped, sender weighted lowest, reviewable, deletable, resettable); "always" answers create rules | ✅ tests |
| 9 | Room v3 additive migration, tested against real SQLite; text retention 24 h; Privacy center deletions | ✅ 🟡 on-device upgrade not tested |
| 10 | Redesign: Home hero, AI Mode entry, Focus status card, Queue tabs, Insights, Profile hub | ✅ 🟡 not visually checked on a device |
| 11 | 74 JVM unit tests; fresh debug APK | ✅ |
| Extra | Focus coach, energy-aware session length, focus score, timeline, schedule suggestions, emergency bypass, evaluation report, privacy center, goals and streaks | ✅ |
| — | Instrumented, UI or emulator tests | ❌ no device or emulator image available |
