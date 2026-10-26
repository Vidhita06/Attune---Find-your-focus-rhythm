package com.attune.prediction.classifier

/** Strong, explicit evidence that a notification needs attention now. Mirrors ml/attune_features.py. */
enum class Evidence(val label: String) {
    OTP("Looks like a one-time code"),
    SECURITY("Looks like an account-security alert"),
    EMERGENCY("Mentions an emergency (hospital, accident…)"),
    REPEAT_CALLS("Several missed calls in a row")
}

object SafetyRules {
    private val OTP_WORDS = Regex("""\b(otp|one[- ]time|verification code|passcode|security code|login code|sign[- ]in code|auth(entication)? code|2fa)\b""")
    private val OTP_DIGITS = Regex("""(?<!\d)\d{4,8}(?!\d)""")
    private val SECURITY = Regex("""\b(new sign-?in|password (was )?changed|unusual (activity|transaction)|suspicious (activity|login|sign-?in)|someone tried to sign in|card \S+ (was )?blocked|account (is |was |has been )?(locked|compromised|suspended))\b""")
    private val EMERGENCY = Regex("""\b(emergency|hospital|accident|ambulance|fainted|tabiyat kharab)\b""")
    private val REPEAT_CALLS = Regex("""\b([2-9]|\d{2,}) missed calls\b""")
    private val FINANCE = Regex("""\b(debited|credited|a/c|avl bal|balance|upi|card ending|transaction)\b""")
    private val PROMO = setOf("shopping", "promotion")

    /**
     * Wording that MIGHT mean someone needs you now (English + Hinglish). A hint, not proof: the engine only uses it to keep a
     * message visible (REVIEW) instead of holding it. Kotlin-engine-only; not part of the ML feature set or route().
     */
    private val URGENT_HINT = Regex(
        """\b(urgent(ly)?|asap|immediately|right now|call (me|us|back)|ring me|pick up (the )?(phone|call)|need (you|help|to talk)|help me|very important|""" +
        """jaldi|turant|fauran|abhi (call|phone|baat)|(call|phone) karo|zaroori|zaruri|heart attack|chest pain|icu|ambulance|police|accident|hospital|haspital|madad|bachao)\b""")
    /** Marketing context: "urgent" next to these is a sales tactic, not a person needing you. */
    private val MARKETING = Regex("""\b(\d+ ?% ?off|sale|offer|discount|cashback|coupon|deals?|promo|limited time|last chance|hurry|buy now|shop now|subscribe|unsubscribe|lottery|prize|voucher)\b""")
    private val PLACEHOLDER = Regex(
        """^(sensitive notification content hidden|notification content hidden|contents? (is |are )?hidden|content hidden|message hidden|hidden message|""" +
        """(you have )?(a |an |\d+ )?(new )?messages?|new notification|\d+ new (messages?|notifications?))$""")

    private fun joined(title: String?, text: String?) = TextFeaturizer.fold((title ?: "") + " " + (text ?: ""))

    /**
     * True when there is nothing to judge: no text at all, or only a lock-screen/redaction placeholder such as
     * "Sensitive notification content hidden". Missing content is never evidence of a distraction.
     */
    fun isRedactedOrEmpty(text: String?): Boolean {
        val t = TextFeaturizer.fold(text).trim().trimEnd('.', '!').replace(Regex("""\s+"""), " ")
        return t.isEmpty() || PLACEHOLDER.matches(t)
    }

    /** True if the wording might signal urgency or an emergency. Never proof; callers prefer REVIEW over HOLD when it matches. */
    fun urgencyHint(title: String?, text: String?, category: String?): Boolean =
        category !in PROMO && joined(title, text).let { URGENT_HINT.containsMatchIn(it) && !MARKETING.containsMatchIn(it) }

    fun looksLikeOtp(title: String?, text: String?): Boolean { val s = joined(title, text); return OTP_WORDS.containsMatchIn(s) && OTP_DIGITS.containsMatchIn(s) }

    fun strongEvidence(title: String?, text: String?, category: String?): Evidence? {
        val s = joined(title, text)
        return when {
            looksLikeOtp(title, text) -> Evidence.OTP
            SECURITY.containsMatchIn(s) -> Evidence.SECURITY
            category !in PROMO && EMERGENCY.containsMatchIn(s) -> Evidence.EMERGENCY
            REPEAT_CALLS.containsMatchIn(s) -> Evidence.REPEAT_CALLS
            else -> null
        }
    }

    /** Sensitive = previews are hidden by default in Attune's UI (codes, security alerts, money). */
    fun isSensitive(title: String?, text: String?, category: String?): Boolean {
        val s = joined(title, text)
        return looksLikeOtp(title, text) || SECURITY.containsMatchIn(s) || category == "finance" || FINANCE.containsMatchIn(s)
    }
}
