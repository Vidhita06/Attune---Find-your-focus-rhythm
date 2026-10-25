# Attune on-device notification classifier

This folder contains the reproducible pipeline behind **AI Mode**: the dataset generator, training and evaluation, and the exported model that the Android app runs on-device.

> **The data is synthetic.** It is template-generated and annotated by the author. It was not collected from real people. The metrics below come from a held-out *synthetic* test split. They show that the pipeline works and how it compares with the old rules. They do **not** show how accurate it is on real notifications.

## Reproduce

```bash
pip install numpy scipy scikit-learn       # versions used: numpy 2.3.4, scikit-learn 1.7.2, Python 3.14.0
python ml/generate_dataset.py              # -> ml/data/notifications.csv  (seeded: identical output every run)
python ml/train.py                         # -> model, golden vectors, evaluation; copies the model + evaluation into app assets
./gradlew :prediction:test                 # ModelParityTest checks the Kotlin port against the Python outputs
```

`train.py` runs in about 20 seconds on a laptop CPU.

The app never calls Python. The model ships as `app/src/main/assets/models/attune_text_model.json`, and the app runs it in plain Kotlin.

## Files

| Path | What it is |
|---|---|
| `generate_dataset.py` | Templates, slot values, noise and the gold-label rules |
| `attune_features.py` | Featurizer, safety rules, link rules and the routing policy. **Mirrored in Kotlin** (see the file header) |
| `train.py` | Training, calibration, threshold selection, evaluation and export |
| `data/notifications.csv` | 3,105 rows from 266 templates (train 1,978 · validation 514 · test 613) |
| `artifacts/attune_text_model.json` | Exported model (~200 KB, 3,268 features) |
| `artifacts/golden_vectors.json` | 65 cases with exact expected features, probabilities, routing, risk, link verdict and sensitivity, used by the Kotlin parity test |
| `reports/evaluation.json`, `reports/evaluation.md` | All computed metrics |

## Dataset and annotation schema

Each row has: `template_id, family, package, app_label, app_category, title, text, language, importance_label, urgency_label, distraction_label, action_required, contains_link, link_safety, ambiguous, risk_category, recommended_action, split`.

| Label | Values | Meaning |
|---|---|---|
| `importance_label` | low / medium / high | How much it matters to the user |
| `urgency_label` | not_urgent / urgent | Whether it needs attention soon |
| `distraction_label` | not_distracting / distracting | Likely to pull the user into scrolling or chatting |
| `action_required` | 0 / 1 | Asks the user to do something |
| `contains_link`, `link_safety` | none / unknown / caution | Kept separate from importance. *Unknown* is **not** unsafe. *Caution* only when there is a concrete local signal: a raw IP address, punycode, a spam-associated TLD, or a brand name on an odd domain |
| `ambiguous` | 0 / 1 | Too little context to judge (e.g. "call me", "?", link-only) |
| `risk_category` | LOW / MEDIUM / HIGH / UNKNOWN | Focus risk, derived by the rule in `gold()` |
| `recommended_action` | allow / defer / review | **allow** if importance is high or it is urgent; **review** if ambiguous; otherwise **defer** |

**Coverage:**
- casual chat (English and Hinglish), urgent personal messages, college deadlines and announcements, work incidents, requests and chatter
- appointments, OTPs, security alerts, bank transactions, bills and bank promotions
- promotions with **fake urgency**, scams with links, social, video, games, news and delivery
- newsletters, link shares, link-only messages, very short or ambiguous messages, and unknown numbers

**Deliberately hard cases:**
- **The same senders appear with different intents.** "Mom" sends casual, urgent and ambiguous messages, and "HDFC Bank" sends transactions, fraud alerts and promotions.
- **Some casual messages misuse the word "urgent"**, e.g. "URGENT: need memes lol".
- **The text is noisy**: abbreviations, misspellings, casing and dropped punctuation.

**Leakage control:**
- Each pattern is its own template.
- The split is done by template within each family, so near-duplicate variants never cross splits.
- Exact duplicates are removed.

## Model

There is one L2-regularised logistic-regression head per signal: importance (3-class softmax), and urgency, distraction and action-required (sigmoid each).

**Features** are binary and L2-normalised:
- word unigrams and bigrams
- character trigrams
- sender-title words
- app category
- meta flags (URL, link-only, short, code-like number, `?`, `!`, mostly capitals)

**Preprocessing:** text is folded to ASCII and URLs and numbers are replaced with placeholder tokens.

**Fitting:**
- The regularisation strength `C` is chosen per head on validation macro-F1.
- Each head gets temperature scaling fitted on the validation split.
- The routing thresholds are chosen on the validation split only. The objective weights a wrongly held important message 5× more than a distraction that gets through.

**Why not TFLite:** TFLite was not in the offline build cache. For a linear model, a JSON file plus a sparse dot product in Kotlin is smaller and simpler, and needs no native libraries.

**Inference speed:** measured on the JVM at about 32 µs per notification in a unit test. The AI Mode screen can measure it on a phone, but that has not been done yet.

## Results (held-out synthetic test split, 613 rows)

| Head | Accuracy | Macro-F1 | Majority-class F1 | ECE before → after temperature |
|---|---|---|---|---|
| importance | 0.819 | 0.787 | 0.212 | 0.155 → 0.070 |
| urgency | 0.914 | 0.859 | 0.436 | 0.011 → 0.025 (temperature made it slightly worse) |
| distraction | 0.868 | 0.861 | 0.365 | 0.054 → 0.041 |
| action | 0.819 | 0.804 | 0.376 | 0.041 → 0.038 |

- The keyword baseline for importance reaches macro-F1 0.355.
- The importance "high" class has recall **0.56**. Many high-importance messages from unseen templates are predicted as medium. The routing policy compensates for this by sending them to *review* rather than holding them.

**Routing, compared with the rule policy from Attune v1** (which queues by app category):

| Metric | Trained pipeline | v1 rules |
|---|---|---|
| Important or urgent messages wrongly held (false deferral) | **7.3 %** | 77.5 % |
| Urgent messages wrongly held | **0 %** | 75.5 % |
| Ambiguous messages silently held | **20 %** | 100 % |
| Distractions held | 71.9 % | 92.5 % |
| Distractions let straight through | 0.7 % | 7.5 % |
| Routing accuracy (allow / defer / review) | 74.1 % | 66.2 % |

**Trade-off:** the pipeline is cautious. 110 of the 402 deferrable test items went to *review*, which means they stay visible, so it protects focus less than the v1 rules. On fake-urgency promotions and scams, only 23 of 47 were held and 24 went to review. None were allowed straight through.

**Where it is weak:**
- **Links:** routing accuracy is 0.53 on messages containing links.
- **Job-offer emails from unseen templates:** 11 were wrongly held.
- **Unknown-number messages** such as "Regarding your application": 12 were held instead of reviewed.

**Other results:**
- Focus-risk agreement with the annotated risk is 0.63.
- The link-safety rules agree with the annotated link labels 99.9 % of the time. Those rules and labels were written by the same author, so this is a consistency check, not a validation.

## Limitations

- The dataset is synthetic and template-based, so real notifications will be messier and more varied. Expect lower real-world accuracy.
- Language coverage is English and romanised Hinglish only. Non-Latin text is dropped by the ASCII folding.
- The model is linear and has no world knowledge, so it can be fooled by unusual phrasing.
- On-device personalization is a transparent bias layer, not retraining.
- Improving the model requires collecting consented, labelled real data. That has not been done.
