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

    private fun joined(title: String?, text: String?) = TextFeaturizer.fold((title ?: "") + " " + (text ?: ""))

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
