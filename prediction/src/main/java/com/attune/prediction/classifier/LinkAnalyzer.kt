package com.attune.prediction.classifier

/** NONE = no link. UNKNOWN = a link with no local evidence either way (not "safe"). CAUTION = a concrete local warning sign. */
enum class LinkSafety { NONE, UNKNOWN, CAUTION }

data class LinkAssessment(val safety: LinkSafety, val hosts: List<String>, val signals: List<String>) {
    val hasLink get() = hosts.isNotEmpty()
}

/**
 * Local, offline URL inspection. Attune never fetches, opens or uploads a link. The rules only look at the host name,
 * so an UNKNOWN verdict means "Attune can't tell", never "safe". Mirrors ml/attune_features.py.
 */
object LinkAnalyzer {
    private val URL = Regex("""(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|in|ly|co|xyz|top|live|click|be|org|net|io|me|app)\b(?:/\S*)?""")
    private val IP = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val RISKY_TLDS = setOf("xyz", "top", "live", "click", "loan", "work", "zip", "mov", "country", "gq", "tk", "ml", "cf")
    private val BRANDS = setOf("hdfc", "sbi", "icici", "paytm", "amazon", "amaz0n", "flipkart", "paypal", "google", "whatsapp", "netflix", "phonepe")
    val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly", "is.gd", "ow.ly", "shorturl.at")

    fun hosts(text: String?): List<String> = URL.findAll(TextFeaturizer.fold(text)).map { m ->
        m.value.replaceFirst(Regex("^https?://"), "").replaceFirst(Regex("^www\\."), "").split(Regex("[/?#:]"), limit = 2)[0]
    }.toList()

    fun signals(host: String): List<String> {
        val s = mutableListOf<String>()
        if (IP.matches(host)) s += "ip_address"
        if (host.startsWith("xn--") || ".xn--" in host) s += "punycode"
        val tld = if ('.' in host) host.substringAfterLast('.') else ""
        if (tld in RISKY_TLDS) s += "risky_tld"
        val labels = host.split('.')
        if (BRANDS.any { it in host } && (tld in RISKY_TLDS || '-' in labels[0] || labels.size > 3)) s += "brand_lookalike"
        return s
    }

    fun assess(text: String?): LinkAssessment {
        val hs = hosts(text)
        if (hs.isEmpty()) return LinkAssessment(LinkSafety.NONE, emptyList(), emptyList())
        val sig = hs.flatMap { signals(it) }.distinct()
        return LinkAssessment(if (sig.isEmpty()) LinkSafety.UNKNOWN else LinkSafety.CAUTION, hs, sig)
    }

    fun describe(signal: String) = when (signal) {
        "ip_address" -> "Link points to a raw IP address instead of a named website"
        "punycode" -> "Link uses look-alike (punycode) characters"
        "risky_tld" -> "Link uses a domain ending often seen in spam"
        "brand_lookalike" -> "Link names a well-known brand on an unusual domain"
        else -> signal
    }
}
