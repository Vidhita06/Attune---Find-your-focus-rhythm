"""
Train, calibrate and evaluate Attune's on-device notification classifier, then export it for Android.

    python ml/generate_dataset.py
    python ml/train.py

Model: one L2-regularised logistic-regression head per signal (importance: 3 classes; urgency, distraction and
action-required: binary) over sparse binary features (word unigrams/bigrams, character trigrams, sender-title words,
app category, a few meta flags), L2-normalised. Temperature scaling per head is fitted on the validation split.
Routing thresholds are chosen on the validation split only; the test split is used once, for the final report.

Outputs:
  ml/artifacts/attune_text_model.json      model (also copied to app/src/main/assets/models/)
  ml/artifacts/golden_vectors.json         parity fixtures for the Kotlin implementation
  ml/reports/evaluation.json / .md         metrics (evaluation.json also copied to the app assets)
"""
import csv
import json
import math
import os
import platform
import shutil
import sys
import time
from collections import Counter

import numpy as np
import scipy.sparse as sp
import sklearn
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, f1_score, precision_recall_fscore_support

sys.path.insert(0, os.path.dirname(__file__))
import attune_features as F  # noqa: E402

ROOT = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(ROOT, "data", "notifications.csv")
ART = os.path.join(ROOT, "artifacts")
REP = os.path.join(ROOT, "reports")
APP_ASSETS = os.path.join(ROOT, "..", "app", "src", "main", "assets", "models")
MODEL_VERSION = "attune-text-lr-1.0"
MIN_DF = 2
C_GRID = [0.5, 1.0, 2.0, 4.0, 8.0, 16.0]

HEADS = {
    "imp": dict(column="importance_label", classes=["low", "medium", "high"]),
    "urg": dict(column="urgency_label", classes=["not_urgent", "urgent"]),
    "dis": dict(column="distraction_label", classes=["not_distracting", "distracting"]),
    "act": dict(column="action_required", classes=["0", "1"]),
}

# Categories the original rule-based policy (Attune v1) knew; anything else was "unknown" and queued by default.
V1_KNOWN = {"social", "short_video", "messaging", "email", "shopping", "entertainment", "gaming", "health", "productivity", "news"}
V1_QUEUED = {"messaging", "social", "short_video", "entertainment", "gaming", "shopping", "promotion", "news", "email", "unknown"}


def load():
    with open(DATA, encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def build_vocab(rows):
    df = Counter()
    for r in rows:
        df.update(set(F.featurize(r["title"], r["text"], r["app_category"])))
    vocab = sorted(f for f, c in df.items() if c >= MIN_DF)
    return {f: i for i, f in enumerate(vocab)}


def vectorize(rows, vocab):
    data, ind, ptr = [], [], [0]
    for r in rows:
        idx = sorted({vocab[f] for f in F.featurize(r["title"], r["text"], r["app_category"]) if f in vocab})
        v = 1.0 / math.sqrt(len(idx)) if idx else 0.0
        ind.extend(idx)
        data.extend([v] * len(idx))
        ptr.append(len(ind))
    return sp.csr_matrix((np.array(data, dtype=np.float64), np.array(ind, dtype=np.int64), np.array(ptr)), shape=(len(rows), len(vocab)))


def labels(rows, head):
    cls = HEADS[head]["classes"]
    return np.array([cls.index(r[HEADS[head]["column"]]) for r in rows])


def logits(model, X):
    z = X @ model.coef_.T + model.intercept_
    return np.asarray(z)


def probs_from_logits(z, T=1.0):
    z = z / T
    if z.shape[1] == 1:
        return 1.0 / (1.0 + np.exp(-z[:, 0]))
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


def nll(p, y):
    if p.ndim == 1:
        p = np.stack([1 - p, p], axis=1)
    return float(-np.mean(np.log(np.clip(p[np.arange(len(y)), y], 1e-12, 1))))


def fit_temperature(z, y):
    best = (1.0, nll(probs_from_logits(z, 1.0), y))
    for T in np.arange(0.3, 3.01, 0.05):
        v = nll(probs_from_logits(z, T), y)
        if v < best[1]:
            best = (float(round(T, 2)), v)
    return best[0]


def ece(p, y, bins=10):
    if p.ndim == 1:
        p = np.stack([1 - p, p], axis=1)
    conf = p.max(axis=1)
    pred = p.argmax(axis=1)
    total = 0.0
    for b in range(bins):
        lo, hi = b / bins, (b + 1) / bins
        m = (conf > lo) & (conf <= hi)
        if m.any():
            total += m.mean() * abs((pred[m] == y[m]).mean() - conf[m].mean())
    return float(total)


def head_report(y, pred, classes):
    rep = classification_report(y, pred, labels=list(range(len(classes))), target_names=classes, output_dict=True, zero_division=0)
    return dict(
        accuracy=round(float((y == pred).mean()), 4),
        macro_f1=round(float(f1_score(y, pred, average="macro", zero_division=0)), 4),
        per_class={c: {k: round(float(rep[c][k]), 4) for k in ("precision", "recall", "f1-score", "support")} for c in classes},
        confusion_matrix=dict(labels=classes, rows_true_cols_pred=confusion_matrix(y, pred, labels=list(range(len(classes)))).tolist()))


# ------------------------------------------------------------------ baselines ----
URGENT_WORDS = ("urgent", "asap", "now", "immediately", "emergency", "hospital", "otp", "due today", "in 5 min", "jaldi", "abhi")
PROMO_WORDS = ("off", "sale", "offer", "deal", "cashback", "liked", "followed", "posted", "live", "reel", "meme", "lol", "haha")


def keyword_importance(r):
    s = F.ascii_lower(r["title"] + " " + r["text"])
    if any(w in s for w in URGENT_WORDS):
        return 2
    if any(w in s for w in PROMO_WORDS):
        return 0
    return 1


def v1_route(r):
    cat = r["app_category"] if r["app_category"] in V1_KNOWN else "unknown"
    if F.looks_like_otp(r["title"], r["text"]):
        return "allow"
    return "defer" if cat in V1_QUEUED else "allow"


def routing_metrics(rows, preds):
    gold = [r["recommended_action"] for r in rows]
    names = ["allow", "defer", "review"]
    cm = confusion_matrix(gold, preds, labels=names).tolist()
    must = [i for i, g in enumerate(gold) if g == "allow"]
    amb = [i for i, g in enumerate(gold) if g == "review"]
    deferrable = [i for i, g in enumerate(gold) if g == "defer"]
    imp_high = [i for i, r in enumerate(rows) if r["importance_label"] == "high"]
    urgent = [i for i, r in enumerate(rows) if r["urgency_label"] == "urgent"]
    def rate(idx, cond):
        return round(sum(1 for i in idx if cond(preds[i])) / len(idx), 4) if idx else None
    p, rc, f, s = precision_recall_fscore_support(gold, preds, labels=names, zero_division=0)
    return dict(
        n=len(rows),
        accuracy=round(sum(1 for g, q in zip(gold, preds) if g == q) / len(rows), 4),
        per_action={n: dict(precision=round(float(p[i]), 4), recall=round(float(rc[i]), 4), f1=round(float(f[i]), 4), support=int(s[i])) for i, n in enumerate(names)},
        confusion_matrix=dict(labels=names, rows_true_cols_pred=cm),
        false_deferral_rate_needs_attention=rate(must, lambda q: q == "defer"),
        false_deferral_rate_high_importance=rate(imp_high, lambda q: q == "defer"),
        false_deferral_rate_urgent=rate(urgent, lambda q: q == "defer"),
        important_or_urgent_not_deferred_recall=rate(must, lambda q: q != "defer"),
        ambiguous_silently_deferred_rate=rate(amb, lambda q: q == "defer"),
        distractions_let_through_rate=rate(deferrable, lambda q: q == "allow"),
        distractions_deferred_recall=rate(deferrable, lambda q: q == "defer"))


def model_probs(models, temps, X):
    out = {}
    for h, m in models.items():
        out[h] = probs_from_logits(logits(m, X), temps[h])
    return out


def per_row(pr, i):
    return dict(imp=[float(x) for x in pr["imp"][i]], urg=float(pr["urg"][i]), dis=float(pr["dis"][i]), act=float(pr["act"][i]))


def tune_thresholds(rows, pr):
    """Pick routing thresholds on VALIDATION: lowest false-deferral of must-see items first, then fewest distractions let through."""
    best = None
    for allow_imp in (0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6):
        for allow_urg in (0.3, 0.4, 0.5, 0.6):
            for min_conf in (0.4, 0.45, 0.5, 0.55, 0.6):
                for review_imp in (0.15, 0.2, 0.25, 0.3):
                    if review_imp >= allow_imp:
                        continue
                    th = dict(allow_important=allow_imp, allow_urgent=allow_urg, min_confidence=min_conf, review_important=review_imp, short_words=2)
                    preds = [F.route(per_row(pr, i), th, r["title"], r["text"], r["app_category"])[0] for i, r in enumerate(rows)]
                    m = routing_metrics(rows, preds)
                    # objective: false deferral of must-see items is weighted 5x heavier than letting a distraction through,
                    # and sending clear distractions to "review" costs a little.
                    review_of_deferrable = sum(1 for i, r in enumerate(rows) if r["recommended_action"] == "defer" and preds[i] == "review") / max(1, sum(1 for r in rows if r["recommended_action"] == "defer"))
                    score = 5 * m["false_deferral_rate_needs_attention"] + m["distractions_let_through_rate"] + 0.5 * review_of_deferrable + 2 * (m["ambiguous_silently_deferred_rate"] or 0)
                    if best is None or score < best[0] - 1e-9:
                        best = (score, th)
    return best[1]


def main():
    t0 = time.time()
    rows = load()
    split = {s: [r for r in rows if r["split"] == s] for s in ("train", "validation", "test")}
    vocab = build_vocab(split["train"])
    X = {s: vectorize(split[s], vocab) for s in split}

    models, temps, chosen_c = {}, {}, {}
    for h in HEADS:
        best = None
        for c in C_GRID:
            m = LogisticRegression(C=c, max_iter=4000, solver="lbfgs")
            m.fit(X["train"], labels(split["train"], h))
            f = f1_score(labels(split["validation"], h), m.predict(X["validation"]), average="macro")
            if best is None or f > best[0] + 1e-9:
                best = (f, c, m)
        chosen_c[h] = best[1]
        models[h] = best[2]
        temps[h] = fit_temperature(logits(models[h], X["validation"]), labels(split["validation"], h))

    pr_val = model_probs(models, temps, X["validation"])
    thresholds = tune_thresholds(split["validation"], pr_val)

    # ---------------- final evaluation on the held-out TEST split ----------------
    test = split["test"]
    pr = model_probs(models, temps, X["test"])
    heads_eval = {}
    for h, spec in HEADS.items():
        y = labels(test, h)
        p = pr[h]
        pred = p.argmax(axis=1) if p.ndim == 2 else (p >= 0.5).astype(int)
        raw = probs_from_logits(logits(models[h], X["test"]), 1.0)
        maj = Counter(labels(split["train"], h)).most_common(1)[0][0]
        heads_eval[h] = dict(
            model=head_report(y, pred, spec["classes"]),
            majority_baseline=head_report(y, np.full_like(y, maj), spec["classes"]),
            calibration=dict(temperature=temps[h], ece_before=round(ece(raw, y), 4), ece_after=round(ece(p, y), 4)),
            regularisation_C=chosen_c[h])
    heads_eval["imp"]["keyword_baseline"] = head_report(labels(test, "imp"), np.array([keyword_importance(r) for r in test]), HEADS["imp"]["classes"])

    route_preds = [F.route(per_row(pr, i), thresholds, r["title"], r["text"], r["app_category"]) for i, r in enumerate(test)]
    pipeline = routing_metrics(test, [a for a, _ in route_preds])
    v1 = routing_metrics(test, [v1_route(r) for r in test])
    risk_names = ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]
    risk_gold = [r["risk_category"] for r in test]
    risk_pred = [rk for _, rk in route_preds]
    risk_eval = dict(accuracy=round(sum(1 for a, b in zip(risk_gold, risk_pred) if a == b) / len(test), 4),
                     confusion_matrix=dict(labels=risk_names, rows_true_cols_pred=confusion_matrix(risk_gold, risk_pred, labels=risk_names).tolist()))
    link_pred = [F.link_safety(r["text"]) for r in test + split["train"] + split["validation"]]
    link_gold = [r["link_safety"] for r in test + split["train"] + split["validation"]]
    link_eval = dict(note="Rule-based, not learned; evaluated on all rows because it has no trainable parameters.",
                     accuracy=round(sum(1 for a, b in zip(link_gold, link_pred) if a == b) / len(link_gold), 4),
                     confusion_matrix=dict(labels=["none", "unknown", "caution"], rows_true_cols_pred=confusion_matrix(link_gold, link_pred, labels=["none", "unknown", "caution"]).tolist()))

    # per-language / hard-case slices of the pipeline
    def slice_metrics(pred_fn):
        out = {}
        for name, sel in [("hinglish", lambda r: r["language"] == "hinglish"), ("english", lambda r: r["language"] == "en"),
                          ("contains_link", lambda r: r["contains_link"] == "1"), ("ambiguous", lambda r: r["ambiguous"] == "1"),
                          ("fake_urgency_promotions", lambda r: r["family"] in ("promotion", "promotion_hinglish", "fake_urgent_casual", "scam"))]:
            idx = [i for i, r in enumerate(test) if sel(r)]
            if idx:
                out[name] = dict(n=len(idx), routing_accuracy=round(sum(1 for i in idx if pred_fn(i) == test[i]["recommended_action"]) / len(idx), 4),
                                 deferred=sum(1 for i in idx if pred_fn(i) == "defer"), allowed=sum(1 for i in idx if pred_fn(i) == "allow"),
                                 review=sum(1 for i in idx if pred_fn(i) == "review"))
        return out

    # ---------------- export ----------------
    os.makedirs(ART, exist_ok=True)
    os.makedirs(REP, exist_ok=True)
    inv = sorted(vocab, key=vocab.get)
    model_json = dict(
        format="attune-logreg-v1", model_version=MODEL_VERSION, trained_on="synthetic dataset ml/data/notifications.csv (see ml/README.md)",
        features=inv,
        heads={h: dict(type="softmax" if HEADS[h]["classes"].__len__() > 2 else "sigmoid", classes=HEADS[h]["classes"], temperature=temps[h],
                       weights=[[round(float(w), 5) for w in row] for row in models[h].coef_], bias=[round(float(b), 5) for b in models[h].intercept_])
               for h in HEADS},
        thresholds=thresholds)
    model_path = os.path.join(ART, "attune_text_model.json")
    with open(model_path, "w", encoding="utf-8") as fh:
        json.dump(model_json, fh, separators=(",", ":"))
    size = os.path.getsize(model_path)

    # Golden vectors use the ROUNDED exported weights so the Kotlin port can be compared exactly.
    def exported_probs(title, text, cat):
        fs = [vocab[f] for f in F.featurize(title, text, cat) if f in vocab]
        idx = sorted(set(fs))
        v = 1 / math.sqrt(len(idx)) if idx else 0.0
        out = {}
        for h, hd in model_json["heads"].items():
            z = [sum(wrow[i] for i in idx) * v + b for wrow, b in zip(hd["weights"], hd["bias"])]
            z = [x / hd["temperature"] for x in z]
            if hd["type"] == "sigmoid":
                out[h] = 1 / (1 + math.exp(-z[0]))
            else:
                mx = max(z)
                e = [math.exp(x - mx) for x in z]
                out[h] = [x / sum(e) for x in e]
        return out, idx

    golden = []
    sample = [r for i, r in enumerate(test) if i % 9 == 0][:60]
    extra = [dict(title="Mom", text="call me", app_category="messaging"), dict(title="", text="https://bit.ly/3xYz9", app_category="messaging"),
             dict(title="AX-HDFCBK", text="482913 is your OTP. Do not share", app_category="finance"),
             dict(title="Ünïcødé ✓", text="Café ☕ at 5? «quoted» İstanbul www.Example.COM/Path", app_category="unknown"),
             dict(title="", text="", app_category="unknown")]
    for r in sample + extra:
        p, idx = exported_probs(r["title"], r["text"], r["app_category"])
        action, risk = F.route(p, thresholds, r["title"], r["text"], r["app_category"])
        golden.append(dict(title=r["title"], text=r["text"], category=r["app_category"], features=F.featurize(r["title"], r["text"], r["app_category"]),
                           probs=p, route=action, risk=risk, link_safety=F.link_safety(r["text"]),
                           strong_evidence=F.strong_evidence(r["title"], r["text"], r["app_category"]), sensitive=F.is_sensitive(r["title"], r["text"], r["app_category"])))
    with open(os.path.join(ART, "golden_vectors.json"), "w", encoding="utf-8") as fh:
        json.dump(dict(model_version=MODEL_VERSION, cases=golden), fh, ensure_ascii=False, indent=1)

    evaluation = dict(
        model_version=MODEL_VERSION, generated_at=time.strftime("%Y-%m-%d %H:%M:%S"), dataset="SYNTHETIC — template-generated, author-annotated",
        caveat="Metrics are measured on a held-out split of synthetic, template-generated data (split by template). They do not show real-world accuracy.",
        environment=dict(python=platform.python_version(), sklearn=sklearn.__version__, numpy=np.__version__),
        dataset_sizes={s: len(v) for s, v in split.items()}, templates=len({r["template_id"] for r in rows}), vocabulary_size=len(vocab),
        model_size_bytes=size, thresholds=thresholds, heads=heads_eval,
        routing=dict(trained_pipeline=pipeline, v1_rule_policy_baseline=v1, slices=dict(trained_pipeline=slice_metrics(lambda i: route_preds[i][0]),
                                                                                          v1_rule_policy_baseline=slice_metrics(lambda i: v1_route(test[i])))),
        focus_risk=risk_eval, link_safety_rules=link_eval, training_seconds=round(time.time() - t0, 1))
    with open(os.path.join(REP, "evaluation.json"), "w", encoding="utf-8") as fh:
        json.dump(evaluation, fh, indent=1)
    write_markdown(evaluation)

    os.makedirs(APP_ASSETS, exist_ok=True)
    shutil.copy(model_path, os.path.join(APP_ASSETS, "attune_text_model.json"))
    shutil.copy(os.path.join(REP, "evaluation.json"), os.path.join(APP_ASSETS, "evaluation.json"))
    print(json.dumps(dict(vocab=len(vocab), size_bytes=size, thresholds=thresholds, C=chosen_c, temps=temps,
                          imp_macro_f1=heads_eval["imp"]["model"]["macro_f1"], imp_keyword_f1=heads_eval["imp"]["keyword_baseline"]["macro_f1"],
                          pipeline_false_deferral=pipeline["false_deferral_rate_needs_attention"], v1_false_deferral=v1["false_deferral_rate_needs_attention"],
                          pipeline_acc=pipeline["accuracy"], v1_acc=v1["accuracy"], ambiguous_deferred=pipeline["ambiguous_silently_deferred_rate"],
                          distractions_deferred=pipeline["distractions_deferred_recall"]), indent=1))


def write_markdown(e):
    L = [f"# Attune classifier evaluation ({e['model_version']})", "", f"> {e['caveat']}", "",
         f"Generated {e['generated_at']} · Python {e['environment']['python']} · scikit-learn {e['environment']['sklearn']}", "",
         f"Rows: train {e['dataset_sizes']['train']}, validation {e['dataset_sizes']['validation']}, test {e['dataset_sizes']['test']} "
         f"({e['templates']} templates, split by template). Vocabulary {e['vocabulary_size']} features. Model file {e['model_size_bytes']:,} bytes.", "",
         "## Signal heads (test split)", "", "| Head | Model accuracy | Model macro-F1 | Majority macro-F1 | ECE before → after temperature |", "|---|---|---|---|---|"]
    for h, v in e["heads"].items():
        L.append(f"| {h} | {v['model']['accuracy']} | {v['model']['macro_f1']} | {v['majority_baseline']['macro_f1']} | {v['calibration']['ece_before']} → {v['calibration']['ece_after']} (T={v['calibration']['temperature']}) |")
    L += ["", f"Keyword baseline for importance: macro-F1 {e['heads']['imp']['keyword_baseline']['macro_f1']}.", "", "### Importance per class (test)", "",
          "| Class | Precision | Recall | F1 | Support |", "|---|---|---|---|---|"]
    for c, v in e["heads"]["imp"]["model"]["per_class"].items():
        L.append(f"| {c} | {v['precision']} | {v['recall']} | {v['f1-score']} | {int(v['support'])} |")
    L += ["", "## Routing decision (allow / defer / review), test split", "", "| Metric | Trained pipeline | v1 rule policy |", "|---|---|---|"]
    a, b = e["routing"]["trained_pipeline"], e["routing"]["v1_rule_policy_baseline"]
    for k in ("accuracy", "false_deferral_rate_needs_attention", "false_deferral_rate_high_importance", "false_deferral_rate_urgent",
              "important_or_urgent_not_deferred_recall", "ambiguous_silently_deferred_rate", "distractions_deferred_recall", "distractions_let_through_rate"):
        L.append(f"| {k} | {a[k]} | {b[k]} |")
    L += ["", "Confusion matrix, trained pipeline (rows = gold, columns = predicted; order allow, defer, review):", "", "```"]
    L += [str(r) for r in a["confusion_matrix"]["rows_true_cols_pred"]]
    L += ["```", "", f"Thresholds (chosen on validation): `{json.dumps(e['thresholds'])}`", "",
          f"Focus-risk accuracy vs annotated risk: {e['focus_risk']['accuracy']}. Link-safety rules accuracy: {e['link_safety_rules']['accuracy']} ({e['link_safety_rules']['note']})", ""]
    with open(os.path.join(REP, "evaluation.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(L))


if __name__ == "__main__":
    main()
