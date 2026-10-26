package com.attune.prediction.decision


/**
 * Explicit feedback the user can give. [importanceVote] is the only thing lightweight personalization uses; the
 * "always" kinds create explicit rules instead (see the app's RulesRepository) and carry no vote.
 */
enum class FeedbackKind(val importanceVote: Double, val label: String) {
    CORRECT(0.0, "Prediction was right"),
    WRONGLY_DEFERRED(1.0, "Wrongly marked as distracting"),
    ACTUALLY_IMPORTANT(1.0, "Actually important"),
    NOT_IMPORTANT(-1.0, "Not important"),
    OPENED_IMMEDIATELY(0.5, "Opened it straight away"),
    DEFERRED_FINE(-0.5, "Waiting was fine"),
    ALWAYS_ALLOW_APP(0.0, "Always allow this app"),
    ALWAYS_ALLOW_CONTACT(0.0, "Always allow this contact"),
    ALWAYS_ALLOW_CATEGORY(0.0, "Always allow this type"),
    ALWAYS_DEFER_CATEGORY(0.0, "Always hold this type during focus")
}

enum class PreferenceScope(val weight: Double) { APP(1.0), CATEGORY(0.6), SENDER(0.5) }

data class FeedbackSignal(val kind: FeedbackKind, val packageName: String, val category: String, val senderKey: String?)

/** One reviewable learned preference: e.g. "WhatsApp: +0.33 importance from 1 feedback". */
data class LearnedPreference(val scope: PreferenceScope, val key: String, val events: Int, val netVotes: Double, val bias: Double)

data class PersonalBias(val total: Double, val parts: List<LearnedPreference>) { val isZero get() = parts.isEmpty() || total == 0.0 }

/**
 * Transparent, conservative personalization (NOT model retraining):
 *   bias(scope) = weight(scope) * STEP * netVotes / (events + PRIOR)
 * so a single "actually important" on an app moves the importance logit by 1.0 * 1.0 * 1/3 ≈ 0.33, consistent feedback
 * approaches ±1.0 per scope, and the summed bias is clamped to ±[MAX_TOTAL]. Sender history is weighted lowest and can
 * never override strong evidence or explicit rules, which are evaluated before the model in [NotificationDecisionEngine].
 */
class PersonalizationProfile(val preferences: List<LearnedPreference>) {
    private val byKey = preferences.associateBy { it.scope to it.key }

    fun biasFor(packageName: String, category: String, senderKey: String?): PersonalBias {
        val parts = listOfNotNull(byKey[PreferenceScope.APP to packageName], byKey[PreferenceScope.CATEGORY to category],
            senderKey?.let { byKey[PreferenceScope.SENDER to it] }).filter { it.bias != 0.0 }
        return PersonalBias(parts.sumOf { it.bias }.coerceIn(-MAX_TOTAL, MAX_TOTAL), parts)
    }

    companion object {
        const val STEP = 1.0
        const val PRIOR = 2.0
        const val MAX_TOTAL = 1.5
        const val SENDER_SPILLOVER = 0.5
        val EMPTY = PersonalizationProfile(emptyList())

        fun from(signals: List<FeedbackSignal>): PersonalizationProfile {
            val acc = LinkedHashMap<Pair<PreferenceScope, String>, Pair<Int, Double>>()
            for (s in signals) {
                if (s.kind.importanceVote == 0.0) continue
                val keys = listOfNotNull(PreferenceScope.APP to s.packageName, PreferenceScope.CATEGORY to s.category, s.senderKey?.let { PreferenceScope.SENDER to it })
                // Feedback about one identifiable sender counts fully for that sender but only half for the app and category, so
                // "Rahul's memes are unimportant" does not quietly change how every other chat in that app is treated.
                for (k in keys) { val (n, v) = acc[k] ?: (0 to 0.0); val share = if (s.senderKey != null && k.first != PreferenceScope.SENDER) SENDER_SPILLOVER else 1.0
                    acc[k] = (n + 1) to (v + share * s.kind.importanceVote) }
            }
            return PersonalizationProfile(acc.map { (k, nv) ->
                LearnedPreference(k.first, k.second, nv.first, nv.second, k.first.weight * STEP * nv.second / (nv.first + PRIOR))
            })
        }
    }
}

/** Keyed hash used for sender ids. Returns null if the key is unavailable (then no sender id is produced at all). */
fun interface SenderHasher { fun mac(data: ByteArray): ByteArray? }

/**
 * Sender id = HMAC-SHA256(per-install secret, package | normalized title), truncated. Without the secret a dictionary of
 * contact names cannot be tested against stored ids (the earlier unsalted SHA-256 could). The app installs a hasher whose key
 * lives in Android Keystore; until then (and in unit tests) a random per-process key is used, which is safe but not stable.
 * If the keystore is unusable the app installs [SenderHasher] returning null, so sender-level personalization is simply off.
 */
object SenderKey {
    private class ProcessRandom : SenderHasher {
        private val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        override fun mac(data: ByteArray): ByteArray? = javax.crypto.Mac.getInstance("HmacSHA256").run {
            init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256")); doFinal(data) }
    }
    @Volatile private var hasher: SenderHasher = ProcessRandom()
    fun install(h: SenderHasher) { hasher = h }

    fun of(packageName: String, title: String?): String? {
        val t = title?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val d = runCatching { hasher.mac("$packageName|$t".toByteArray(Charsets.UTF_8)) }.getOrNull() ?: return null
        return d.take(12).joinToString("") { "%02x".format(it) }
    }
    /** A masked hint for review screens, e.g. "R••••l". */
    fun hint(title: String?): String? {
        val t = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (t.length <= 2) t.first() + "•" else t.first() + "•".repeat((t.length - 2).coerceAtMost(4)) + t.last()
    }
}
