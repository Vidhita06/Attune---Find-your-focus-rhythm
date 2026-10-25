package com.attune.prediction.policy

enum class Verdict { IGNORE, ALLOW, QUEUE, FILTER }
data class PolicyDecision(val verdict: Verdict, val category: String, val reason: String)

data class IncomingNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    /** android.app.Notification.category as set by the sending app, if any. */
    val androidCategory: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val isClearable: Boolean
)

data class PolicySettings(
    val queuedCategories: Set<String>,
    val allowedPackages: Set<String>,
    val priorityContacts: Set<String>,
    val allowCallsAndAlarms: Boolean,
    val allowOtp: Boolean,
    val filterPromotions: Boolean
)

/**
 * Decides what happens to a notification posted during focus. Rules only use metadata that is reasonably reliable
 * (package, Android category, explicit user exceptions). The OTP rule is a best-effort text match and can be turned off.
 * Anything that cannot be classified falls back to the user's category settings.
 */
object NotificationPolicy {
    private val otpKeywords = Regex("""\b(otp|one[- ]time|verification code|passcode|security code|login code|sign[- ]in code|auth(entication)? code|2fa)\b""", RegexOption.IGNORE_CASE)
    private val otpDigits = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    fun categorize(packageCategory: String, androidCategory: String?): String = when {
        androidCategory == "promo" -> "promotion"
        packageCategory != "unknown" -> packageCategory
        androidCategory == "email" -> "email"
        androidCategory == "msg" -> "messaging"
        androidCategory == "social" -> "social"
        else -> "unknown"
    }

    fun looksLikeOtp(title: String?, text: String?): Boolean {
        val s = listOfNotNull(title, text).joinToString(" ")
        return otpKeywords.containsMatchIn(s) && otpDigits.containsMatchIn(s)
    }

    fun matchesPriorityContact(title: String?, contacts: Set<String>): String? {
        val t = title?.trim()?.lowercase() ?: return null
        return contacts.firstOrNull { c -> val n = c.trim().lowercase(); n.isNotEmpty() && Regex("""(^|\W)${Regex.escape(n)}($|\W)""").containsMatchIn(t) }
    }

    fun decide(ownPackage: String, n: IncomingNotification, packageCategory: String, s: PolicySettings): PolicyDecision {
        val cat = categorize(packageCategory, n.androidCategory)
        return when {
            n.packageName == ownPackage -> PolicyDecision(Verdict.IGNORE, cat, "Attune's own notification")
            n.isOngoing || !n.isClearable -> PolicyDecision(Verdict.IGNORE, cat, "Ongoing notifications (music, navigation, calls in progress) are left alone")
            n.isGroupSummary -> PolicyDecision(Verdict.IGNORE, cat, "Group summary; individual notifications are handled instead")
            n.packageName in s.allowedPackages -> PolicyDecision(Verdict.ALLOW, cat, "You allowed this app to interrupt focus")
            s.allowCallsAndAlarms && (n.androidCategory == "call" || n.androidCategory == "alarm") -> PolicyDecision(Verdict.ALLOW, cat, "Calls and alarms marked by the app are allowed")
            matchesPriorityContact(n.title, s.priorityContacts) != null -> PolicyDecision(Verdict.ALLOW, cat, "Matches a priority contact you added")
            s.allowOtp && looksLikeOtp(n.title, n.text) -> PolicyDecision(Verdict.ALLOW, cat, "Looks like a one-time code (best-effort match)")
            cat == "promotion" && s.filterPromotions -> PolicyDecision(Verdict.FILTER, cat, "Promotional (marked by the app); filtered by your setting")
            cat in s.queuedCategories -> PolicyDecision(Verdict.QUEUE, cat, "Queued: ${cat.replace('_', ' ')} is set to wait during focus")
            else -> PolicyDecision(Verdict.ALLOW, cat, "This category is not set to be queued")
        }
    }
}
