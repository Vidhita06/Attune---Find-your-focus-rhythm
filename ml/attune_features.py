"""
Feature extraction and decision rules shared by training and evaluation.

THIS FILE IS MIRRORED IN KOTLIN:
  prediction/src/main/java/com/attune/prediction/classifier/TextFeaturizer.kt   (featurize)
  prediction/src/main/java/com/attune/prediction/classifier/SafetyRules.kt      (strong evidence, sensitivity)
  prediction/src/main/java/com/attune/prediction/classifier/LinkAnalyzer.kt     (link safety)
  prediction/src/main/java/com/attune/prediction/decision/RiskPolicy.kt         (risk + routing from probabilities)
Parity is checked by prediction/src/test/.../ModelParityTest.kt using ml/artifacts/golden_vectors.json.
"""
import re

URL_RE = re.compile(r"(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|in|ly|co|xyz|top|live|click|be|org|net|io|me|app)\b(?:/\S*)?")
_UPPER = str.maketrans("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz")


def ascii_lower(s):
    """Lowercases A-Z and replaces every non-ASCII character with a space, so regexes behave identically in Python and Java."""
    return "".join(c if ord(c) < 128 else " " for c in (s or "").translate(_UPPER))


def tokens(s):
    """ASCII-lowercase, replace URLs, split on anything that is not [a-z0-9], classify pure-digit tokens."""
    s = URL_RE.sub(" urltoken ", ascii_lower(s))
    out = []
    for t in re.split(r"[^a-z0-9]+", s):
        if not t:
            continue
        if t.isdigit():
            t = "codetoken" if 4 <= len(t) <= 8 else "numtoken"
        out.append(t)
    return out


SPECIAL = {"urltoken", "codetoken", "numtoken"}


def word_count(text):
    return sum(1 for t in tokens(text) if t not in SPECIAL)


def featurize(title, text, category):
    body = tokens(text)
    feats = []
    seen = set()

    def add(f):
        if f not in seen:
            seen.add(f)
            feats.append(f)

    for t in body:
        add("w:" + t)
    for a, b in zip(body, body[1:]):
        add("b:" + a + "_" + b)
    for t in body:
        if t in SPECIAL or len(t) < 2:
            continue
        p = "<" + t + ">"
        for i in range(len(p) - 2):
            add("c:" + p[i:i + 3])
    for t in tokens(title):
        add("t:" + t)
    add("a:" + (category or "unknown"))
    raw = text or ""
    words = [t for t in body if t not in SPECIAL]
    if "urltoken" in body:
        add("m:url")
        if not words:
            add("m:linkonly")
    if len(words) <= 2:
        add("m:short")
    if "codetoken" in body:
        add("m:code")
    if "?" in raw:
        add("m:q")
    if "!" in raw:
        add("m:excl")
    letters = [c for c in raw if "a" <= c.lower() <= "z" and c.isascii()]
    if len(letters) >= 4 and sum(1 for c in letters if "A" <= c <= "Z") / len(letters) >= 0.6:
        add("m:caps")
    return feats


# ------------------------------------------------------------------ strong-evidence & sensitivity rules ----
OTP_WORDS = re.compile(r"\b(otp|one[- ]time|verification code|passcode|security code|login code|sign[- ]in code|auth(entication)? code|2fa)\b")
OTP_DIGITS = re.compile(r"(?<!\d)\d{4,8}(?!\d)")
SECURITY = re.compile(r"\b(new sign-?in|password (was )?changed|unusual (activity|transaction)|suspicious (activity|login|sign-?in)|someone tried to sign in|card \S+ (was )?blocked|account (is |was |has been )?(locked|compromised|suspended))\b")
EMERGENCY = re.compile(r"\b(emergency|hospital|accident|ambulance|fainted|tabiyat kharab)\b")
REPEAT_CALLS = re.compile(r"\b([2-9]|\d{2,}) missed calls\b")
FINANCE_WORDS = re.compile(r"\b(debited|credited|a/c|avl bal|balance|upi|card ending|transaction)\b")
PROMO_CATEGORIES = {"shopping", "promotion"}


def looks_like_otp(title, text):
    s = ascii_lower((title or "") + " " + (text or ""))
    return bool(OTP_WORDS.search(s) and OTP_DIGITS.search(s))


def strong_evidence(title, text, category):
    """Returns a reason id if there is strong, explicit evidence the notification needs attention now, else None."""
    s = ascii_lower((title or "") + " " + (text or ""))
    if looks_like_otp(title, text):
        return "otp"
    if SECURITY.search(s):
        return "security"
    if category not in PROMO_CATEGORIES and EMERGENCY.search(s):
        return "emergency"
    if REPEAT_CALLS.search(s):
        return "repeat_calls"
    return None


def is_sensitive(title, text, category):
    s = ascii_lower((title or "") + " " + (text or ""))
    return looks_like_otp(title, text) or bool(SECURITY.search(s)) or category == "finance" or bool(FINANCE_WORDS.search(s))


# ------------------------------------------------------------------ link analysis (local only, never fetches) ----
SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly", "is.gd", "ow.ly", "shorturl.at"}
RISKY_TLDS = {"xyz", "top", "live", "click", "loan", "work", "zip", "mov", "country", "gq", "tk", "ml", "cf"}
BRANDS = {"hdfc", "sbi", "icici", "paytm", "amazon", "amaz0n", "flipkart", "paypal", "google", "whatsapp", "netflix", "phonepe"}
LINK_URL_RE = re.compile(r"(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|in|ly|co|xyz|top|live|click|be|org|net|io|me|app)\b(?:/\S*)?")


def extract_hosts(text):
    hosts = []
    for m in LINK_URL_RE.finditer(ascii_lower(text)):
        u = m.group(0)
        u = re.sub(r"^https?://", "", u)
        u = re.sub(r"^www\.", "", u)
        host = re.split(r"[/?#:]", u, maxsplit=1)[0]
        hosts.append(host)
    return hosts


def link_signals(host):
    sig = []
    if re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", host):
        sig.append("ip_address")
    if host.startswith("xn--") or ".xn--" in host:
        sig.append("punycode")
    tld = host.rsplit(".", 1)[-1] if "." in host else ""
    if tld in RISKY_TLDS:
        sig.append("risky_tld")
    labels = host.split(".")
    if any(b in host for b in BRANDS) and (tld in RISKY_TLDS or "-" in labels[0] or len(labels) > 3):
        sig.append("brand_lookalike")
    return sig


def link_safety(text):
    """'none' (no link) | 'unknown' (no local evidence either way) | 'caution' (concrete local signal)."""
    hosts = extract_hosts(text)
    if not hosts:
        return "none"
    return "caution" if any(link_signals(h) for h in hosts) else "unknown"


# ------------------------------------------------------------------ risk + routing policy from model outputs ----
def route(probs, thresholds, title, text, category):
    """probs: dict with imp=[low,medium,high], urg, dis, act (positive-class probabilities)."""
    p_low, p_med, p_high = probs["imp"]
    ev = strong_evidence(title, text, category)
    words = word_count(text)
    hosts = extract_hosts(text)
    conf = max(probs["imp"])
    if ev:
        return "allow", "LOW" if probs["dis"] < 0.5 else "MEDIUM"
    if (hosts and words == 0) or (words <= thresholds["short_words"] and not hosts):
        return "review", "UNKNOWN"
    important = p_high >= thresholds["allow_important"] or probs["urg"] >= thresholds["allow_urgent"]
    if important:
        risk = "MEDIUM" if probs["dis"] >= 0.5 else "LOW"
        return "allow", risk
    if conf < thresholds["min_confidence"] or p_high >= thresholds["review_important"]:
        return "review", "UNKNOWN"
    if probs["dis"] >= 0.5:
        risk = "HIGH"
    elif p_med >= max(p_low, p_high) and probs["act"] >= 0.5:
        risk = "MEDIUM"
    else:
        risk = "LOW"
    return "defer", risk
