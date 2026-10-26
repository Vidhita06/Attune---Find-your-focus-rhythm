package com.attune.prediction

import com.attune.prediction.classifier.LinkAnalyzer
import com.attune.prediction.classifier.LinkSafety
import com.attune.prediction.classifier.TextFeaturizer
import com.attune.prediction.decision.*
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import com.attune.prediction.policy.PolicySettings
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Evaluates the DEPLOYED decision path (model + safety rules + guard layer in NotificationDecisionEngine) on the held-out test split of the
 * SYNTHETIC dataset, and compares it with the model-only route that ml/train.py reports. It also checks the split for near-duplicate leakage.
 * Writes ml/reports/engine_routing_eval.md. These numbers describe a template-generated, author-annotated dataset; they are NOT real-world accuracy.
 * The assertions only enforce safety properties (they never encode a target accuracy).
 */
class RoutingEvaluationTest {
    private class Row(val m: Map<String, String>) { operator fun get(k: String) = m.getValue(k) }

    private fun parseCsv(text: String): List<Row> {
        val rows = mutableListOf<List<String>>(); var cur = mutableListOf<String>(); val f = StringBuilder(); var q = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                q && c == '"' && text.getOrNull(i + 1) == '"' -> { f.append('"'); i++ }
                c == '"' -> q = !q
                !q && c == ',' -> { cur += f.toString(); f.clear() }
                !q && (c == '\n' || c == '\r') -> { if (c == '\r' && text.getOrNull(i + 1) == '\n') i++; cur += f.toString(); f.clear(); if (cur.any { it.isNotEmpty() }) rows += cur; cur = mutableListOf() }
                else -> f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || cur.isNotEmpty()) { cur += f.toString(); rows += cur }
        val head = rows.first()
        return rows.drop(1).map { r -> Row(head.indices.associate { head[it] to r.getOrElse(it) { "" } }) }
    }

    private val rows by lazy { parseCsv(File(ModelParityTest.root, "ml/data/notifications.csv").readText(Charsets.UTF_8)) }
    private val test by lazy { rows.filter { it["split"] == "test" } }
    private val train by lazy { rows.filter { it["split"] == "train" } }

    private val policy = PolicySettings(setOf("messaging", "social", "short_video", "shopping", "promotion", "news", "email", "unknown", "productivity", "finance", "health", "education", "entertainment", "gaming"),
        emptySet(), emptySet(), allowCallsAndAlarms = true, allowOtp = true, filterPromotions = false)
    private val settings = DecisionSettings(aiMode = true, policy = policy)
    private val engine = NotificationDecisionEngine(ModelParityTest.model)

    private fun engineRoute(r: Row): String {
        val n = IncomingNotification(r["package"], r["title"], r["text"], null, false, false, true)
        return when (engine.decide(DecisionInput(n, r["app_category"], SessionPhase.FOCUS), settings, emptyList(), PersonalizationProfile.EMPTY, "com.attune.app").routing) {
            Routing.DELIVER, Routing.IGNORE -> "allow"; Routing.DEFER, Routing.FILTER -> "defer"; Routing.REVIEW -> "review" }
    }

    private fun modelOnlyRoute(r: Row): String {
        val p = ModelParityTest.model.classify(r["title"], r["text"], r["app_category"])
        return when (RiskPolicy.route(NotificationDecisionEngine.applyBias(p, 0.0), ModelParityTest.model.thresholds, r["title"], r["text"], r["app_category"]).routing) {
            Routing.DELIVER -> "allow"; Routing.REVIEW -> "review"; else -> "defer" }
    }

    private val names = listOf("allow", "defer", "review")
    private fun matrix(gold: List<String>, pred: List<String>) = names.map { g -> names.map { p -> gold.indices.count { gold[it] == g && pred[it] == p } } }
    private fun pct(n: Int, d: Int) = if (d == 0) "n/a" else "%.1f%% (%d/%d)".format(100.0 * n / d, n, d)
    private fun table(m: List<List<Int>>) = "| true \\ predicted | allow | defer(HOLD) | review |\n|---|---|---|---|\n" + names.indices.joinToString("\n") { "| ${names[it]} | ${m[it].joinToString(" | ")} |" }
    private fun prf(m: List<List<Int>>) = "| class | precision | recall | support |\n|---|---|---|---|\n" + names.indices.joinToString("\n") { c ->
        val tp = m[c][c]; val col = m.sumOf { it[c] }; val row = m[c].sum()
        "| ${names[c]} | ${if (col == 0) "n/a" else "%.3f".format(tp.toDouble() / col)} | ${if (row == 0) "n/a" else "%.3f".format(tp.toDouble() / row)} | $row |" }

    private fun section(title: String, sel: List<Row>, f: (Row) -> String): String {
        val gold = sel.map { it["recommended_action"] }; val pred = sel.map(f); val m = matrix(gold, pred)
        val important = sel.indices.filter { sel[it]["importance_label"] == "high" }; val urgent = sel.indices.filter { sel[it]["urgency_label"] == "urgent" }
        val mustAllow = sel.indices.filter { gold[it] == "allow" }; val amb = sel.indices.filter { sel[it]["ambiguous"] == "1" }
        val deferrable = sel.indices.filter { gold[it] == "defer" }
        return """### $title (n=${sel.size})

${table(m)}

${prf(m)}

- Important (importance=high) messages held (**false-HOLD**): ${pct(important.count { pred[it] == "defer" }, important.size)}
- Urgent messages held: ${pct(urgent.count { pred[it] == "defer" }, urgent.size)}
- Messages that should be allowed but were held: ${pct(mustAllow.count { pred[it] == "defer" }, mustAllow.size)}
- Recall of important/urgent-as-visible (allow or review): ${pct(mustAllow.count { pred[it] != "defer" }, mustAllow.size)}
- Ambiguous messages routed to REVIEW: ${pct(amb.count { pred[it] == "review" }, amb.size)}; silently held: ${pct(amb.count { pred[it] == "defer" }, amb.size)}
- Clear distractions actually held (utility): ${pct(deferrable.count { pred[it] == "defer" }, deferrable.size)}
"""
    }

    private fun shingles(r: Row): Set<String> = TextFeaturizer.tokens(r["text"]).map { if (it == "codetoken" || it == "numtoken") "#" else it }.filter { it != "urltoken" }.toSet()
    private fun jaccard(a: Set<String>, b: Set<String>) = if (a.isEmpty() && b.isEmpty()) 1.0 else a.intersect(b).size.toDouble() / a.union(b).size

    @Test fun evaluateDeployedRoutingAndWriteReport() {
        val engineP = test.map(::engineRoute); val modelP = test.map(::modelOnlyRoute)
        val sb = StringBuilder("# Routing evaluation of the deployed decision engine (generated by RoutingEvaluationTest)\n\n")
        sb.append("> **Synthetic data.** The ${test.size} rows are the held-out test split of a template-generated, author-annotated dataset (ml/data/notifications.csv). ")
        sb.append("These numbers show how the pipeline behaves on that data. They are **not** real-world accuracy, and nothing here was tuned on the test split by this report.\n\n")
        sb.append("Engine = model + safety rules + guard layer (urgent wording, burst, redaction, priority, link). Model-only = the route ml/train.py reports. Mapping: ALLOW=DELIVER, HOLD=DEFER/FILTER, REVIEW=REVIEW. Focus session, every category queued, no user rules, no personalization.\n\n")
        sb.append(section("Engine, all test rows", test) { engineRoute(it) }); sb.append("\n")
        sb.append(section("Model-only route, all test rows (comparison)", test) { modelOnlyRoute(it) }); sb.append("\n")
        for ((name, sel) in listOf("Rows containing a link" to test.filter { it["contains_link"] == "1" }, "Hinglish rows" to test.filter { it["language"] == "hinglish" },
            "Fake-urgency promotions / scams / casual" to test.filter { it["family"] in setOf("promotion", "promotion_hinglish", "fake_urgent_casual", "scam") }))
            if (sel.isNotEmpty()) { sb.append(section("Engine: $name", sel) { engineRoute(it) }); sb.append("\n") }

        // Link analysis is evaluated by itself against the dataset's own link labels (none / unknown / caution)
        val linkRows = rows.filter { it["split"] != "" }
        val ln = listOf("none", "unknown", "caution", "high_risk")
        val lm = listOf("none", "unknown", "caution").map { g -> ln.map { p -> linkRows.count { it["link_safety"] == g && LinkAnalyzer.assess(it["text"]).safety.name.lowercase() == p } } }
        sb.append("### Link analysis (separate from importance), all ${linkRows.size} rows\n\nRule-based and untrained. Gold labels come from the old, coarser Python rules (caution = risky ending or brand look-alike), so disagreement is expected where the new rules are stricter (shorteners) or more selective (an unusual ending alone).\n\n")
        sb.append("| gold \\ new analyzer | ${ln.joinToString(" | ")} |\n|---|---|---|---|---|\n" + lm.indices.joinToString("\n") { "| ${listOf("none", "unknown", "caution")[it]} | ${lm[it].joinToString(" | ")} |" } + "\n\n")

        // leakage
        val trainSets = train.map(::shingles)
        var ge80 = 0; var ge90 = 0; var exact = 0
        for (r in test) { val s = shingles(r); val best = trainSets.maxOf { jaccard(s, it) }; if (best >= 0.8) ge80++; if (best >= 0.9) ge90++; if (best == 1.0) exact++ }
        val templates = test.map { it["template_id"] }.toSet().intersect(train.map { it["template_id"] }.toSet())
        sb.append("### Train/test leakage check\n\n- Templates shared between train and test: **${templates.size}** (the split is grouped by template).\n")
        sb.append("- Test rows whose text token set is >= 0.8 similar (Jaccard, digits/links normalised) to some training row: ${pct(ge80, test.size)}; >= 0.9: ${pct(ge90, test.size)}; identical: ${pct(exact, test.size)}.\n")
        sb.append("- Near-identical wording across *different* templates inflates scores; this is a known weakness of the synthetic set (see ml/README.md).\n")
        File(ModelParityTest.root, "ml/reports/engine_routing_eval.md").writeText(sb.toString(), Charsets.UTF_8)

        // safety properties only: no accuracy targets
        assertEquals(0, templates.size)
        val strong = test.indices.filter { engineP[it] == "defer" && (test[it]["urgency_label"] == "urgent" && test[it]["importance_label"] == "high") }
        assertTrue("urgent+high-importance rows held: ${strong.map { test[it]["text"] }}", strong.isEmpty())
        // The guard layer only removes holds. The one way the engine may hold something the model-only route kept visible is the link-neutral
        // reading (the model sees the text without its URL), so every such row must contain a link and none may be gold "allow".
        val newHolds = test.indices.filter { engineP[it] == "defer" && modelP[it] != "defer" }
        assertTrue("holds created without a link: ${newHolds.filter { LinkAnalyzer.hosts(test[it]["text"]).isEmpty() }.map { test[it]["text"] }}", newHolds.all { LinkAnalyzer.hosts(test[it]["text"]).isNotEmpty() })
        assertTrue("link-neutral reading held something that should be allowed: ${newHolds.filter { test[it]["recommended_action"] == "allow" }.map { test[it]["text"] }}", newHolds.none { test[it]["recommended_action"] == "allow" })
        File(ModelParityTest.root, "ml/reports/engine_routing_eval.md").appendText("\n- Rows held by the engine that the model-only route kept visible (link-neutral reading): ${newHolds.size}; gold labels of those rows: ${newHolds.groupingBy { test[it]["recommended_action"] }.eachCount()}\n", Charsets.UTF_8)
    }

    @Test fun datasetHasBothLanguagesAndLinkRows() {
        assertTrue(test.any { it["language"] == "hinglish" } && test.any { it["language"] == "en" } && test.any { it["contains_link"] == "1" })
        assertTrue(LinkAnalyzer.assess("x").safety == LinkSafety.NONE)
    }
}
