package com.attune.prediction

import com.attune.prediction.classifier.*
import com.attune.prediction.decision.NotificationDecisionEngine
import com.attune.prediction.decision.RiskPolicy
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The Kotlin featurizer, model, rules and routing must reproduce what ml/train.py exported (golden_vectors.json). */
class ModelParityTest {
    companion object {
        val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val modelJson: String by lazy { File(root, "app/src/main/assets/models/attune_text_model.json").readText() }
        val model: LogRegClassifier by lazy { LogRegClassifier.fromJson(modelJson) }
    }

    @Test fun goldenVectorsMatch() {
        val golden = JsonParser.parseString(File(root, "ml/artifacts/golden_vectors.json").readText(Charsets.UTF_8)).asJsonObject
        assertEquals(golden["model_version"].asString, model.modelVersion)
        val cases = golden["cases"].asJsonArray
        assertTrue(cases.size() >= 30)
        for (c in cases.map { it.asJsonObject }) {
            val title = c["title"].asString; val text = c["text"].asString; val cat = c["category"].asString
            assertEquals("features for '$text'", c["features"].asJsonArray.map { it.asString }, TextFeaturizer.featurize(title, text, cat))
            val p = model.classify(title, text, cat)
            val probs = c["probs"].asJsonObject
            val imp = probs["imp"].asJsonArray.map { it.asDouble }
            for (i in 0..2) assertEquals("imp[$i] for '$text'", imp[i], p.importance[i], 1e-9)
            assertEquals(probs["urg"].asDouble, p.urgent, 1e-9)
            assertEquals(probs["dis"].asDouble, p.distracting, 1e-9)
            assertEquals(probs["act"].asDouble, p.actionRequired, 1e-9)
            val o = RiskPolicy.route(NotificationDecisionEngine.applyBias(p, 0.0), model.thresholds, title, text, cat)
            assertEquals("route for '$text'", c["route"].asString, o.routing.name.lowercase().replace("deliver", "allow"))
            assertEquals("risk for '$text'", c["risk"].asString, o.risk.name)
            // The link rules moved to Kotlin-only (LinkAnalyzerTest); parity still covers whether a text has a link at all, which feeds routing.
            assertEquals("has link for '$text'", c["link_safety"].asString != "none", LinkAnalyzer.assess(text).hasLink)
            val ev = c["strong_evidence"].let { if (it.isJsonNull) null else it.asString }
            assertEquals("evidence for '$text'", ev, SafetyRules.strongEvidence(title, text, cat)?.name?.lowercase())
            assertEquals("sensitive for '$text'", c["sensitive"].asBoolean, SafetyRules.isSensitive(title, text, cat))
        }
    }

    @Test fun inferenceIsFastEnoughOnJvm() {
        val texts = listOf("call me right now, it's urgent", "Flat 50% off on running shoes! Sale ends tonight", "Rahul liked your photo", "Assignment 3 is due tomorrow")
        repeat(200) { model.classify("Mom", texts[it % 4], "messaging") } // warm-up
        val t0 = System.nanoTime(); val n = 2000
        repeat(n) { model.classify("Mom", texts[it % 4], "messaging") }
        val usPer = (System.nanoTime() - t0) / 1000.0 / n
        println("JVM inference: %.1f µs per notification over %d runs; vocabulary %d; model file %d bytes".format(usPer, n, model.vocabularySize, modelJson.length))
        assertTrue("inference should be well under 5 ms", usPer < 5000)
    }

    @Test fun invalidModelsAreRejectedSafely() {
        for (bad in listOf("", "{", "{}", """{"format":"other"}""", """{"format":"attune-logreg-v1","features":["a"],"heads":{}}""",
                """{"format":"attune-logreg-v1","features":["a","b"],"heads":{"imp":{"type":"softmax","classes":[],"temperature":1,"weights":[[1]],"bias":[0]}}}""")) {
            try { LogRegClassifier.fromJson(bad); fail("accepted invalid model: $bad") } catch (e: ModelFormatException) { /* expected */ }
        }
    }
}
