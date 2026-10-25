# AI Mode, decision flow and architecture

## Modules

| Module | Role |
|---|---|
| `:prediction` (pure Kotlin/JVM) | **Classifier:** `TextFeaturizer`, `LogRegClassifier`, `SafetyRules`, `LinkAnalyzer`. **Decisions:** `NotificationDecisionEngine`, `RiskPolicy`, `Personalization`. Also the existing time-cost baseline (`PredictionEngine`) and `NotificationPolicy`. No Android dependencies, so everything is unit-tested on the JVM |
| `:core-model` (Android library) | Room entities, DAOs and migrations (schema v3) |
| `:app` | **Listener:** `FocusListenerService`. **Repositories:** `DecisionRepository`, `PersonalizationRepository`, `RulesRepository`, `QueueRepository`, `TimelineRepository`, `PrivacyRepository`, `DemoDataRepository`. **Analytics:** `ReportCalculator`, `ProfileCalculator`. **UI:** Compose screens |

The UI and the notification listener depend only on the `NotificationClassifier` interface and the decision engine. A better model can replace `LogRegClassifier` without touching either.

## Notification decision flow

```
Android posts a notification
  → FocusListenerService.onNotificationPosted  (title + text are read in memory only)
  → DecisionRepository.evaluateAndRecord
      → NotificationDecisionEngine.decide
          0. system: Attune's own / ongoing / group summary → IGNORE
             no session + "leave alone outside focus" → IGNORE
          · model signals (AI Mode only): calibrated importance / urgency / distraction / action,
            then a capped personal bias from feedback
          · paused, or outside focus with "observe" on → DELIVER (recorded, never held)
          1. ALLOW rules: allowed apps, calls/alarms, priority contacts, keyword/category allow rules → DELIVER
          2. safety evidence: OTP (if enabled), security alert, emergency words, 2+ missed calls → DELIVER
          3. DEFER rules → DEFER, but REVIEW if the model rates the content clearly important
             filter-promotions setting → FILTER
          4a. AI Mode: RiskPolicy → DELIVER / DEFER / REVIEW plus LOW / MEDIUM / HIGH / UNKNOWN
          4b. AI Mode off, or model missing or failed → category rules (labelled as a fallback)
      → one row in notification_decisions (deduplicated by notification key; metadata, scores and generic reasons only)
  → DEFER / FILTER: added to the queue; Android is asked to cancel the original (best-effort)
    REVIEW: copy added to the queue; the original is left visible
    DELIVER: nothing touched
```

### RiskPolicy (uncertain cases are never silently held)

| Condition | Routing | Focus risk |
|---|---|---|
| Link only, or 2 words or fewer of real text | REVIEW | UNKNOWN |
| P(high importance) ≥ 0.55 or P(urgent) ≥ 0.60 | DELIVER | LOW (MEDIUM if distracting) |
| Top importance probability < 0.60 | REVIEW | UNKNOWN |
| P(high importance) ≥ 0.15 | REVIEW | UNKNOWN |
| Otherwise | DEFER | HIGH if distracting; MEDIUM if medium importance and action needed; else LOW |

The thresholds ship inside the model file, and they were chosen on the validation split.

Importance, urgency, distraction, action-required and link safety are stored and shown **separately**. *Focus risk* is a summary label only. It never replaces the individual signals.

## Personalization (feedback → future decisions)

**Feedback options** on *Before You Open → Correct it / Mark important*:
- correct
- wrongly held
- actually important
- not important
- opened immediately
- waiting was fine
- always allow app / contact / type
- always hold type

**How it works:**
- Each answer is stored as a `feedback_events` row. The sender is stored as a one-way hash plus a masked hint.
- `PersonalizationProfile.from(events)` builds the adjustment for each scope as `weight × netVotes / (events + 2)`.
  - The weights are app 1.0, category 0.6, sender 0.5.
  - The total is clamped to ±1.5 log-odds.
  - One "actually important" adds about +0.33 for the app.
- The adjustment shifts the importance logits (and distraction at half strength) before `RiskPolicy` runs.
- Rules and safety checks are evaluated first, so personalization can never override them.
- "Always …" answers create explicit rules: an allowed app or contact in DataStore, or a category rule in `user_rules`.
- The profile is recomputed from stored feedback every time. Deleting feedback, or resetting under *Learned preferences* or the *Privacy center*, removes its effect immediately.
- Demo feedback affects only demo samples.

**This is not model retraining.** The model file never changes on the device.

**Tests:**
- `PersonalizationTest`: a single event gives a modest shift, consistent events are capped, feedback changes a borderline recommendation, and deleting feedback resets it.
- `DecisionEngineTest`: sender history never overrides urgent content, and rules take priority.

## Links and sensitive notifications

**Links:**
- URLs are found with a local regex.
- `LinkAnalyzer` only inspects host names: IP addresses, punycode, risky TLDs and brand look-alikes.
- Attune never fetches, opens or uploads a link.
- A link-only message is UNKNOWN and REVIEW.

**Sensitive notifications** (OTP, security, money):
- They are flagged `isSensitive`.
- Their previews are masked in the queue and on *Before You Open* until the user taps to show them (a setting controls this).
- No explanation terms are computed for them.

## Before You Open

The screen shows:
- the recommendation, the focus-risk chip and who decided (rule, safety check, model, or model plus feedback)
- a "rule overrode the AI" banner that shows what the model alone would have done
- each signal with its probability, the link verdict, and a confidence bar labelled as calibrated on synthetic data
- the reasons, and the words that pushed the model towards "important" (computed live, never stored)
- Open now, Delay, Dismiss, **Mark important** and **Correct it**
- the separate, collapsible time-cost baseline estimate and its counterfactual

## Android limitations (also shown in the app)

- A `NotificationListenerService` sees a notification **after** it is posted. The sound or vibration may already have happened, and heads-up notifications may flash.
- `cancelNotification` is best-effort and only works for clearable notifications. Attune **cannot re-post or restore** another app's notification. After focus it shows a summary notification that opens its own queue instead.
- Notification access must be granted by the user. Sideloaded apps on Android 13+ may need *Allow restricted settings*.
- Opening the original target uses the notification's PendingIntent. Android 14+ may block background activity starts. Attune then falls back to the app's launcher.
- Opens made directly from the notification shade are invisible to Attune, so "interruptions" are undercounted.
