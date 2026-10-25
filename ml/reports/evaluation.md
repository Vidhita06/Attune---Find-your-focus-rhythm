# Attune classifier evaluation (attune-text-lr-1.0)

> Metrics are measured on a held-out split of synthetic, template-generated data (split by template). They do not show real-world accuracy.

Generated 2026-10-26 03:53:02 · Python 3.14.0 · scikit-learn 1.7.2

Rows: train 1978, validation 514, test 613 (266 templates, split by template). Vocabulary 3268 features. Model file 202,628 bytes.

## Signal heads (test split)

| Head | Model accuracy | Model macro-F1 | Majority macro-F1 | ECE before → after temperature |
|---|---|---|---|---|
| imp | 0.8189 | 0.7866 | 0.2116 | 0.1549 → 0.0697 (T=0.6) |
| urg | 0.9135 | 0.8586 | 0.4361 | 0.0108 → 0.0246 (T=0.9) |
| dis | 0.8679 | 0.8608 | 0.3648 | 0.0537 → 0.0408 (T=1.2) |
| act | 0.8189 | 0.8042 | 0.3758 | 0.0412 → 0.0376 (T=1.25) |

Keyword baseline for importance: macro-F1 0.3554.

### Importance per class (test)

| Class | Precision | Recall | F1 | Support |
|---|---|---|---|---|
| low | 0.7901 | 0.9509 | 0.8631 | 285 |
| medium | 0.8743 | 0.796 | 0.8333 | 201 |
| high | 0.8161 | 0.5591 | 0.6636 | 127 |

## Routing decision (allow / defer / review), test split

| Metric | Trained pipeline | v1 rule policy |
|---|---|---|
| accuracy | 0.7406 | 0.6623 |
| false_deferral_rate_needs_attention | 0.0728 | 0.7748 |
| false_deferral_rate_high_importance | 0.0866 | 0.7323 |
| false_deferral_rate_urgent | 0.0 | 0.7554 |
| important_or_urgent_not_deferred_recall | 0.9272 | 0.2252 |
| ambiguous_silently_deferred_rate | 0.2 | 1.0 |
| distractions_deferred_recall | 0.7189 | 0.9254 |
| distractions_let_through_rate | 0.0075 | 0.0746 |

Confusion matrix, trained pipeline (rows = gold, columns = predicted; order allow, defer, review):

```
[117, 11, 23]
[3, 289, 110]
[0, 12, 48]
```

Thresholds (chosen on validation): `{"allow_important": 0.55, "allow_urgent": 0.6, "min_confidence": 0.6, "review_important": 0.15, "short_words": 2}`

Focus-risk accuracy vs annotated risk: 0.6346. Link-safety rules accuracy: 0.9987 (Rule-based, not learned; evaluated on all rows because it has no trainable parameters.)
