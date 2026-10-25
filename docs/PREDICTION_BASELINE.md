# Attune time-cost baseline (`baseline-v1`)

> Scope: this baseline estimates only *time away / recovery* on Before You Open. Notification **routing** (hold / let through / review) is done by the trained on-device classifier described in `AI_MODE_AND_ARCHITECTURE.md` and `ml/README.md`.

This is a **transparent, deterministic rule-based estimator**. It is **not a trained machine-learning model**, and its accuracy has **not been validated**. It exists so the prototype can show the Before You Open experience honestly, and so that it can be replaced by a trained model later.

## Interface

`prediction/src/main/java/com/attune/prediction/AttentionPredictor.kt`

```kotlin
interface AttentionPredictor { val modelVersion: String; fun predict(input: PredictionInput): Prediction }
```

The app binds it in `app/.../di/AppModule.kt` (`PredictionModule.providePredictor`). A trained model only has to implement this interface and be bound there.

## Two separate outputs

| Output | Meaning |
|---|---|
| `timeAwayMin` | Estimated minutes spent in the other app |
| `recoveryMin` | Estimated minutes afterwards to get back into the task |

They are computed and displayed separately and are never summed into one "time" value, except in the clearly labelled counterfactual ("time away + recovery").

## Algorithm (`PredictionEngine`)

1. **Category prior**: `T0` and `R0` come from `app/src/main/assets/priors.json`. These are starting assumptions, not measurements.
2. **Personal blend**: `n` is the number of the user's own reports for the same category, and `w = n / (n + 5)`. Then `T = w*mean(T_user) + (1-w)*T0`, and the same for `R`.
3. **Context rules** (fixed and labelled in the UI):
   - 2 bundled notifications: time away ×1.1. 3 or more: ×1.25.
   - 21:00–05:00: time away ×1.15. This is an assumption.
   - At least 20 minutes into an active focus session: recovery ×1.25.
   - On a break, or with no session running: recovery ×0.4.
4. **Risk** from `T + R`: HIGH if at least 20 min, MEDIUM if at least 9 min, otherwise LOW.
5. **Confidence** is `w`, clamped to 0.1–0.9. **Cold start** applies while `n < 3`. The displayed range is ±60% at cold start and narrows towards ±20% as `w` grows.

## Counterfactual (`CounterfactualEstimator`)

The counterfactual answers "What might have happened if I had opened this immediately?":

- **Delayed or dismissed during focus**: estimated minutes protected are `T + R`, using the estimate recorded at decision time.
- **Delayed or dismissed outside focus**: 0 minutes protected.
- **Opened**: shows the measured return time (an upper bound) or the user-reported time. No "protected" figure is given.

Every result carries an uncertainty note. It is an estimate of an outcome that did not happen, and one decision is not causal evidence.

## Data recorded for future training (observable only)

`user_actions` stores the following for each decision:

- category
- session phase
- the predicted `T`/`R`
- whether a launch was attempted, and its result
- the launch time and the time the user returned to Attune, when measured
- the session outcome, filled in when the session ends

`feedback` stores the user's self-reported time away and recovery. Missing values stay `NULL`; nothing is imputed.
