package com.attune.prediction.classifier

import java.net.IDN
import java.util.Locale

/**
 * NONE = no link. UNKNOWN = a link with no local warning sign (NOT "safe": local rules cannot verify a destination).
 * CAUTION = a concrete local warning sign. HIGH_RISK = several strong signs that together look like phishing.
 * There is deliberately no "safe" level.
 */
enum class LinkSafety { NONE, UNKNOWN, CAUTION, HIGH_RISK }

/** One link found in a notification. [host] is the ASCII (punycode) hostname without "www." or port; never the path or query. */
data class LinkFinding(val host: String, val safety: LinkSafety, val signals: List<String>, val notes: List<String>)

/**
 * [safety] is the worst level over all [findings]. [signals] are the warning-sign ids behind CAUTION/HIGH_RISK, [notes] are
 * observations that do not raise the level on their own (plain http, unusual ending). [limitations] says what the analysis could not do.
 */
data class LinkAssessment(val safety: LinkSafety, val hosts: List<String>, val signals: List<String>,
                          val findings: List<LinkFinding> = emptyList(), val notes: List<String> = emptyList(),
                          val limitations: List<String> = emptyList()) {
    val hasLink get() = hosts.isNotEmpty()
}

/**
 * Local, offline URL inspection. Attune never fetches, opens, resolves or uploads a link: only the text of the URL is examined, and
 * only the host name decides the result (path and query are looked at for two structural patterns, never stored or shown).
 * Link risk is independent of message importance; see NotificationDecisionEngine.
 *
 * Known limits (also returned in [LinkAssessment.limitations]): no reputation data, no public-suffix list (the registrable domain is
 * approximated), no redirect following, and the brand list is small. A shortened link's destination is unknowable offline.
 */
object LinkAnalyzer {
    private val SCHEME = Regex("""^(?:https?|ftp)://""", RegexOption.IGNORE_CASE)
    private val IPV4 = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")
    private val LABEL = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
    private val HOSTLIKE = Regex("""^[\p{L}\p{N}][\p{L}\p{N}.-]*\.[\p{L}\p{N}-]{2,24}$""")
    private val LEADING = Regex("""^[^\p{L}\p{N}]+""")
    private val TRAILING = Regex("""[\s.,;:!?)\]}>"'’”]+$""")
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "ac", "edu", "nic", "res")
    private val AUTH_PATH = Regex("""(login|signin|sign-in|verify|verification|kyc|secure|account|update|otp|password|wallet|refund|claim)""")

    /** Endings accepted for scheme-less text like "bit.ly/x". Broad on purpose, but still a list: otherwise "e.g" or "file.txt" would be links. */
    private val TLDS = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "info", "biz", "name", "pro", "mobi", "app", "dev", "io", "ai", "co", "me", "tv", "cc", "ly", "gl", "gd", "gg", "to", "ws", "fm", "sh",
        "xyz", "top", "live", "click", "link", "site", "online", "store", "shop", "tech", "club", "vip", "icu", "work", "loan", "zip", "mov", "country", "cloud", "page", "world", "today", "news",
        "life", "space", "website", "fun", "buzz", "bar", "rest", "monster", "digital", "agency", "bank", "finance", "money", "pay", "review", "support", "services", "solutions", "network",
        "in", "us", "uk", "ca", "au", "nz", "de", "fr", "es", "it", "nl", "be", "ch", "at", "se", "no", "dk", "fi", "pl", "ru", "ua", "cz", "gr", "pt", "ie", "tr", "il", "ae", "sa", "qa", "pk", "bd", "lk",
        "np", "sg", "my", "id", "ph", "th", "vn", "cn", "hk", "tw", "jp", "kr", "br", "ar", "mx", "cl", "co", "pe", "za", "ng", "ke", "eg", "ma", "eu", "asia", "su", "tk", "ml", "ga", "cf", "gq", "sbi")
    private val RISKY_TLDS = setOf("xyz", "top", "live", "click", "loan", "work", "zip", "mov", "country", "gq", "tk", "ml", "cf", "ga", "icu", "monster", "buzz", "rest", "vip")

    val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly", "is.gd", "ow.ly", "shorturl.at", "buff.ly", "t.ly", "tiny.cc", "lnkd.in", "s.id", "v.gd", "bl.ink", "short.io", "rebrand.ly", "wa.me")
    /** wa.me is a WhatsApp click-to-chat redirector rather than a shortener; it is still destination-hiding but not a general one. */
    private val BENIGN_REDIRECTORS = setOf("wa.me")

    /** Brand -> domains the brand really uses (registrable domains). A brand name on any other domain is a warning sign. */
    private val BRANDS: Map<String, Set<String>> = mapOf(
        "hdfc" to setOf("hdfcbank.com", "hdfc.com", "hdfcbank.net", "hdfclife.com"), "sbi" to setOf("sbi.co.in", "onlinesbi.sbi", "onlinesbi.com", "sbi.bank.in"),
        "icici" to setOf("icicibank.com", "icicilombard.com", "iciciprulife.com"), "axisbank" to setOf("axisbank.com"), "paytm" to setOf("paytm.com", "paytmbank.com"),
        "phonepe" to setOf("phonepe.com"), "amazon" to setOf("amazon.com", "amazon.in", "amazon.co.uk", "amazonaws.com", "amzn.to", "amzn.in"),
        "flipkart" to setOf("flipkart.com", "fkrt.it"), "paypal" to setOf("paypal.com", "paypal.me"), "google" to setOf("google.com", "google.co.in", "goo.gl", "gstatic.com", "googleapis.com", "withgoogle.com"),
        "whatsapp" to setOf("whatsapp.com", "whatsapp.net", "wa.me"), "netflix" to setOf("netflix.com"), "microsoft" to setOf("microsoft.com", "live.com", "office.com", "outlook.com", "microsoftonline.com", "office365.com", "sharepoint.com"),
        "apple" to setOf("apple.com", "icloud.com"), "instagram" to setOf("instagram.com"), "facebook" to setOf("facebook.com", "fb.com", "fb.me"),
        "irctc" to setOf("irctc.co.in"), "uidai" to setOf("uidai.gov.in"), "incometax" to setOf("incometax.gov.in", "incometaxindia.gov.in"))

    /** Names a brand goes by inside domain labels (compared after look-alike folding). Exact match, or a long name followed by a short suffix. */
    private val BRAND_TOKENS: Map<String, Set<String>> = mapOf(
        "hdfc" to setOf("hdfc", "hdfcbank"), "sbi" to setOf("sbi", "onlinesbi"), "icici" to setOf("icici", "icicibank"), "axisbank" to setOf("axisbank"),
        "paytm" to setOf("paytm"), "phonepe" to setOf("phonepe"), "amazon" to setOf("amazon"), "flipkart" to setOf("flipkart"), "paypal" to setOf("paypal"),
        "google" to setOf("google"), "whatsapp" to setOf("whatsapp"), "netflix" to setOf("netflix"), "microsoft" to setOf("microsoft"), "apple" to setOf("apple", "icloud"),
        "instagram" to setOf("instagram"), "facebook" to setOf("facebook"), "irctc" to setOf("irctc"), "uidai" to setOf("uidai"), "incometax" to setOf("incometax", "incometaxindia"))

    /** Look-alike characters folded before comparing with brand names. */
    private fun skeleton(s: String): String = s.replace("rn", "m").replace("vv", "w").map { c ->
        when (c) { '0' -> 'o'; '1' -> 'l'; '3' -> 'e'; '5' -> 's'; '@' -> 'a'; '$' -> 's'; else -> c } }.joinToString("")

    private class Parsed(val host: String, val scheme: String?, val path: String, val userInfo: Boolean, val unicode: Boolean, val userInfoDomain: Boolean = false)

    private fun candidates(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(Regex("""\s+""")).mapNotNull { raw ->
            // "see:bit.ly/x", "(www.a.com)", "a.com." → strip decoration; skip e-mail addresses (no scheme and an @ before the first slash)
            var t = raw.replace(LEADING, "").replace(TRAILING, "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // "at:zomato.com" / "visit:bit.ly/x": drop a label prefix, but keep "host:8080" and "https://"
            if (!SCHEME.containsMatchIn(t) && ':' in t) {
                val after = t.substringAfter(':')
                if (after.isNotEmpty() && !after.startsWith("//") && !after.first().isDigit()) t = after.replace(LEADING, "").ifEmpty { return@mapNotNull null }
            }
            val schemeful = SCHEME.containsMatchIn(t)
            if (!schemeful && t.substringBefore('/').contains('@')) return@mapNotNull null
            if (schemeful || t.lowercase(Locale.ROOT).startsWith("www.") || HOSTLIKE.matches(t.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':'))) t else null
        }
    }

    private fun parse(candidate: String): Parsed? {
        val scheme = SCHEME.find(candidate)?.value?.dropLast(3)?.lowercase(Locale.ROOT)
        var rest = candidate.substring(SCHEME.find(candidate)?.value?.length ?: 0)
        val authEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        var authority = rest.substring(0, authEnd); val path = rest.substring(authEnd)
        val userInfo = '@' in authority
        val userInfoDomain = userInfo && '.' in authority.substringBeforeLast('@') // "https://hdfcbank.com@evil.top"
        if (userInfo) authority = authority.substringAfterLast('@')
        authority = authority.substringBefore(':').trimEnd('.').lowercase(Locale.ROOT).removePrefix("www.")
        if (authority.isEmpty() || authority.length > 253 || authority.any { it.isWhitespace() }) return null
        val unicode = authority.any { it.code > 127 }
        val ascii = try { IDN.toASCII(authority, IDN.ALLOW_UNASSIGNED) } catch (e: IllegalArgumentException) { return null }
        val labels = ascii.split('.')
        // A scheme-ful URL whose host has no dot ("https://wwwamazonin/dp") is a mistyped or intranet address, not a normal website: kept as a link
        // (so a link-only message is still recognised) and noted, never trusted.
        val bareHost = scheme != null && labels.size == 1 && LABEL.matches(labels[0])
        if (!bareHost && (labels.size < 2 || labels.any { !LABEL.matches(it) })) {
            if (!IPV4.matches(ascii)) return null
        }
        val tld = labels.last()
        if (!IPV4.matches(ascii) && scheme == null && !candidate.lowercase(Locale.ROOT).startsWith("www.") && tld !in TLDS && !tld.startsWith("xn--")) return null
        if (IPV4.matches(ascii) && ascii.split('.').any { it.toInt() > 255 }) return null
        return Parsed(ascii, scheme, path, userInfo, unicode, userInfoDomain)
    }

    /** Approximation of the registrable domain without a public-suffix list. */
    fun registrableDomain(host: String): String {
        val l = host.split('.')
        if (l.size <= 2 || IPV4.matches(host)) return host
        return if (l.last().length == 2 && l[l.size - 2] in SECOND_LEVEL && l.size >= 3) l.takeLast(3).joinToString(".") else l.takeLast(2).joinToString(".")
    }

    fun hosts(text: String?): List<String> = candidates(text).mapNotNull { parse(it)?.host }

    fun signals(host: String): List<String> = inspect(Parsed(host, null, "", false, false)).signals

    private class Result(val signals: List<String>, val notes: List<String>)

    private fun inspect(p: Parsed): Result {
        val s = linkedSetOf<String>(); val notes = linkedSetOf<String>()
        val host = p.host
        val isIp = IPV4.matches(host)
        val labels = host.split('.')
        val tld = if (isIp) "" else labels.last()
        val reg = registrableDomain(host)
        if (isIp) s += "ip_address"
        if (host.startsWith("xn--") || ".xn--" in host || p.unicode) s += "punycode"
        if (p.userInfo) s += "userinfo"
        if (reg in SHORTENERS && reg !in BENIGN_REDIRECTORS && labels.size <= 3) s += "shortener"
        if (tld in RISKY_TLDS) notes += "risky_tld"
        if (labels.size == 1 && !isIp) notes += "no_domain_ending"
        if (p.scheme == "http") notes += "insecure_http"
        if (p.path.contains(Regex("""[?&](?:url|u|redirect|redirect_uri|next|goto|target|dest|continue)=(?:https?(?::|%3a)|//|%2f%2f)""", RegexOption.IGNORE_CASE))) s += "redirect_param"
        var brandHit = false; var misleading = false
        if (!isIp) {
            val tokens = host.split('.', '-').filter { it.isNotEmpty() }
            for ((brand, official) in BRANDS) {
                if (official.any { host == it || host.endsWith(".$it") }) continue
                if (official.any { host.startsWith("$it.") || host.contains(".$it.") }) misleading = true // hdfcbank.com.verify-now.top
                val names = BRAND_TOKENS[brand].orEmpty()
                if (tokens.any { t -> val k = skeleton(t); names.any { n -> k == n || (n.length >= 6 && k.startsWith(n) && k.length <= n.length + 6) } }) brandHit = true
            }
        }
        if (misleading) s += "misleading_subdomain"
        if (brandHit && !misleading) s += "brand_lookalike"
        // strong combinations are what make this high risk, not any single oddity
        val hyphenated = labels.first().count { it == '-' } >= 1
        val authPath = AUTH_PATH.containsMatchIn(p.path.lowercase(Locale.ROOT))
        val extra = tld in RISKY_TLDS || hyphenated || labels.size > 3 || "punycode" in s || isIp || authPath || p.userInfo
        val high = "misleading_subdomain" in s || "userinfo" in s && (brandHit || p.userInfoDomain) || ("brand_lookalike" in s && extra) || (isIp && authPath && p.scheme == "http")
        if (high) s += "_high"
        if (!high && isIp && authPath) notes += "login_path_on_ip"
        return Result(s.toList(), notes.toList())
    }

    fun assess(text: String?): LinkAssessment {
        val cands = candidates(text)
        if (cands.isEmpty()) return LinkAssessment(LinkSafety.NONE, emptyList(), emptyList())
        val findings = cands.mapNotNull { c -> parse(c)?.let { p ->
            val r = inspect(p)
            val high = "_high" in r.signals
            val sig = r.signals.filter { it != "_high" }
            LinkFinding(p.host, if (high) LinkSafety.HIGH_RISK else if (sig.isEmpty()) LinkSafety.UNKNOWN else LinkSafety.CAUTION, sig, r.notes)
        } }.distinctBy { it.host to it.signals }
        val malformed = cands.size > findings.size
        val limitations = buildList {
            add("Checked locally from the link text only. Nothing was opened, resolved or sent anywhere, so the destination and any redirects are unknown.")
            if (malformed) add("Some link-like text couldn't be read and was ignored.")
            if (findings.any { "shortener" in it.signals }) add("A shortened link hides its real destination.")
        }
        if (findings.isEmpty()) return LinkAssessment(LinkSafety.NONE, emptyList(), emptyList(), limitations = limitations.drop(1))
        val worst = findings.maxBy { it.safety.ordinal }.safety
        return LinkAssessment(worst, findings.map { it.host }.distinct(), findings.flatMap { it.signals }.distinct(), findings,
            findings.flatMap { it.notes }.distinct(), limitations)
    }

    fun describe(signal: String) = when (signal) {
        "ip_address" -> "Link points to a raw IP address instead of a named website"
        "punycode" -> "Link uses look-alike (internationalised / punycode) characters"
        "brand_lookalike" -> "Link names a well-known brand on a domain that isn't the brand's"
        "misleading_subdomain" -> "Link puts a real brand's domain inside a different site's address"
        "userinfo" -> "Link has a name@ prefix that can disguise the real site"
        "shortener" -> "Shortened link: it hides where it really goes (not necessarily harmful)"
        "redirect_param" -> "Link passes another web address to follow (a redirect pattern)"
        "risky_tld" -> "Domain ending is common in spam (weak signal on its own)"
"no_domain_ending" -> "The address has no normal domain ending (mistyped or internal address)"
        "insecure_http" -> "Uses plain http, not https (https alone would not prove safety either)"
        "login_path_on_ip" -> "Login or verification wording on a raw IP address"
        else -> signal
    }

    fun label(s: LinkSafety) = when (s) { LinkSafety.NONE -> "No link"; LinkSafety.UNKNOWN -> "Unknown"; LinkSafety.CAUTION -> "Caution"; LinkSafety.HIGH_RISK -> "High risk" }
}
