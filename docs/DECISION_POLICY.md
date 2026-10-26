# Decision policy (ALLOW / HOLD / REVIEW)

Attune decides with a **hybrid** of four things, in this order. It does not depend on a large keyword list.

| Layer | What it uses | Where |
|---|---|---|
| 1. User choices | Allowed apps, sender levels, keyword/category rules, Android priority conversations, calls and alarms | `NotificationDecisionEngine` steps 1 and 3 |
| 2. Small safety rules | One-time codes, account-security alerts, emergencies, repeated missed calls | `SafetyRules.strongEvidence` (about 5 compact patterns) |
| 3. On-device classifier | Importance, urgency, distraction, action-required, each with a calibrated probability, plus confidence | `LogRegClassifier` + `RiskPolicy` (thresholds ship inside the model file) |
| 4. Guard layer | Anything about to be HELD is checked once more and kept visible if it might matter | `guard()` in the engine; all numbers in `DecisionPolicy` |

Names used in the app: **ALLOW** = `DELIVER`, **HOLD** = `DEFER` (and `FILTER` for your promotions setting), **REVIEW** = `REVIEW` (the original stays visible, a copy is listed in the queue).

## The signals stay separate

Importance, urgency, distraction likelihood, actionability, classification confidence, link risk and your preferences are different values. They are stored and shown separately and are never summed into one score. "Focus risk" is a display label only.

- **Link risk never changes importance.** The model reads the text with URLs removed, so a link (suspicious or not) cannot raise or lower any score (`suspiciousLinkNeverChangesTheImportanceScores`).
- **Sender history never decides importance.** It nudges the model's importance by at most ±1.5 logits, never overrides a safety rule or an explicit rule, and a sender-specific vote counts only half for the whole app and category.

## Decision flow

1. Attune's own, ongoing, non-clearable and group-summary notifications: ignored (never held).
2. No session: ignored or observed only. Paused or break: ALLOW.
3. **ALLOW rules:** allowed app; call/alarm category; *Always allow* sender; Android priority conversation; your allow rules.
4. **Strong evidence** (OTP if enabled, security alert, emergency wording, 2+ missed calls): ALLOW.
5. **Your hold rules** (app, category, keyword, *Hold* sender): HOLD, but REVIEW if the model thinks the message looks important.
6. **Model (AI Mode)** → `RiskPolicy`: link-only or 2 words or fewer: REVIEW. P(high) >= 0.55 or P(urgent) >= 0.60: ALLOW. Top-class confidence < 0.60: REVIEW. P(high) >= 0.15: REVIEW. Otherwise HOLD.
7. **Model missing, failed or AI Mode off:** category rules, but with a model failure everything is REVIEW, and with a missing model only short video, games, shopping, promotions, entertainment and news can be held. (AI Mode off = your category settings, as configured.)
8. **Guard layer**, applied to every HOLD (not to FILTER): becomes REVIEW if any of these holds:

| Guard | Default (`DecisionPolicy`) | Meaning |
|---|---|---|
| Priority sender | on | A sender you marked *Priority*. Never held; ordinary messages are not called emergencies. |
| Urgent wording | on | Compact English/Hinglish phrase list (`urgent`, `asap`, `call me`, `jaldi`, `chest pain`, ...). Ignored for shopping/promotions and when marketing words (`% off`, `sale`, `last chance`, `buy now`, ...) are present. It only keeps a message visible; it never marks anything an emergency. |
| Burst | 3 messages in 120 s | Same conversation (shortcut id, else app + title). Repeated callbacks of one post count once. |
| Follow-up | 2 messages, 300 s | Another message from a conversation Attune just held. |
| Reminder / event | `reminder`, `event` | Android category set by the app. |
| High-risk link | on | Shows the warning. It only ever turns HOLD into REVIEW; it never causes a HOLD. |
| No readable content | on | Empty, or only a placeholder such as "Sensitive notification content hidden". Skipped when you own an explicit app/category hold rule. |

Everything else (burst size, windows, categories, retention) is a field of `DecisionPolicy`; the model thresholds are chosen on the validation split and live in the model file.

## Sender levels

| Level | How | Behaviour |
|---|---|---|
| Always allow | Settings -> Senders -> *Always allow* | Reaches you during focus. |
| Priority sender | *Priority* (rule `PRIORITY`) | Never held. Ordinary messages stay visible in your queue; urgent/security wording is still handled by the safety rules. |
| Normal | no entry | Model decides. |
| Defer eligible distractions | *Hold* (rule `DEFER`) | Held when the model is confident and no guard applies. |

Matching uses the notification **title**, which many messaging apps set to the sender's name. No contacts permission is requested; a contacts picker was judged unnecessary because the title is what Android exposes.

## Android metadata: what is used and what is not

| Signal | Used? |
|---|---|
| `Notification.category` (`call`, `alarm`, `msg`, `email`, `social`, `promo`, `reminder`, `event`) | Yes (allow for call/alarm, categorisation, reminder/event guard) |
| `Notification.shortcutId` | Yes, in memory only, to recognise a conversation for burst detection |
| Ranking `isConversation` + channel `isImportantConversation` (Android 11+) | Yes, as an allow rule; false when unavailable |
| Group summary / ongoing / clearable flags | Yes |
| Channel importance (`Ranking.getImportance`) | **No.** Apps assign HIGH to nearly everything, so it does not discriminate, and low importance must not be read as unimportant. |
| "Call after messages" | No separate signal: calls are already ALLOWed by category. |

## What this does not guarantee

Classification is imperfect and the model was trained on synthetic data. REVIEW is the safe fallback, not a guarantee. See `ml/reports/engine_routing_eval.md` for measured behaviour on the synthetic test split and its limits.
