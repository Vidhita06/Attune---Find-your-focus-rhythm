package com.attune.prediction.classifier

/**
 * Kotlin port of ml/attune_features.py `featurize`. Must stay byte-for-byte compatible with the Python version;
 * ModelParityTest checks this against ml/artifacts/golden_vectors.json.
 */
object TextFeaturizer {
    private val URL = Regex("""(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|in|ly|co|xyz|top|live|click|be|org|net|io|me|app)\b(?:/\S*)?""")
    private val SPLIT = Regex("[^a-z0-9]+")
    private val SPECIAL = setOf("urltoken", "codetoken", "numtoken")

    /** Lowercases A–Z and turns every non-ASCII character into a space, so regexes behave the same as in Python. */
    fun fold(s: String?): String {
        if (s == null) return ""
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(when { c in 'A'..'Z' -> c + 32; c.code < 128 -> c; else -> ' ' })
        return sb.toString()
    }

    fun tokens(s: String?): List<String> =
        URL.replace(fold(s), " urltoken ").split(SPLIT).filter { it.isNotEmpty() }.map { t ->
            if (t.all { it in '0'..'9' }) (if (t.length in 4..8) "codetoken" else "numtoken") else t
        }

    fun wordCount(text: String?) = tokens(text).count { it !in SPECIAL }

    fun featurize(title: String?, text: String?, category: String?): List<String> {
        val body = tokens(text)
        val out = LinkedHashSet<String>()
        body.forEach { out += "w:$it" }
        body.zipWithNext { a, b -> out += "b:${a}_$b" }
        body.forEach { t ->
            if (t in SPECIAL || t.length < 2) return@forEach
            val p = "<$t>"
            for (i in 0 until p.length - 2) out += "c:" + p.substring(i, i + 3)
        }
        tokens(title).forEach { out += "t:$it" }
        out += "a:" + (category?.takeIf { it.isNotEmpty() } ?: "unknown")
        val raw = text ?: ""
        val words = body.count { it !in SPECIAL }
        if ("urltoken" in body) { out += "m:url"; if (words == 0) out += "m:linkonly" }
        if (words <= 2) out += "m:short"
        if ("codetoken" in body) out += "m:code"
        if ('?' in raw) out += "m:q"
        if ('!' in raw) out += "m:excl"
        val letters = raw.filter { it in 'a'..'z' || it in 'A'..'Z' }
        if (letters.length >= 4 && letters.count { it in 'A'..'Z' }.toDouble() / letters.length >= 0.6) out += "m:caps"
        return out.toList()
    }
}
