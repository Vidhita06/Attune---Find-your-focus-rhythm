package com.attune.prediction.classifier

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.math.exp
import kotlin.math.sqrt

/** Calibrated probabilities from the on-device model. importance = [low, medium, high]. */
data class SignalProbabilities(
    val importance: DoubleArray, val urgent: Double, val distracting: Double, val actionRequired: Double, val modelVersion: String
) {
    val pLow get() = importance[0]
    val pMedium get() = importance[1]
    val pHigh get() = importance[2]
    /** Confidence of the importance head (probability of its top class). */
    val confidence get() = importance.max()
    override fun equals(other: Any?) = other is SignalProbabilities && importance.contentEquals(other.importance) && urgent == other.urgent &&
        distracting == other.distracting && actionRequired == other.actionRequired && modelVersion == other.modelVersion
    override fun hashCode() = importance.contentHashCode() * 31 + urgent.hashCode()
}

/** Anything that can score a notification's text. The decision engine and the UI depend only on this interface. */
interface NotificationClassifier {
    val modelVersion: String
    val thresholds: RoutingThresholds
    fun classify(title: String?, text: String?, category: String?): SignalProbabilities
}

data class RoutingThresholds(
    @SerializedName("allow_important") val allowImportant: Double = 0.55,
    @SerializedName("allow_urgent") val allowUrgent: Double = 0.6,
    @SerializedName("min_confidence") val minConfidence: Double = 0.6,
    @SerializedName("review_important") val reviewImportant: Double = 0.15,
    @SerializedName("short_words") val shortWords: Int = 2
)

private class HeadJson(val type: String, val classes: List<String>, val temperature: Double, val weights: List<DoubleArray>, val bias: DoubleArray)
private class ModelJson(val format: String?, @SerializedName("model_version") val modelVersion: String?, val features: List<String>?,
                        val heads: Map<String, HeadJson>?, val thresholds: RoutingThresholds?)

class ModelFormatException(msg: String) : Exception(msg)

/**
 * Multi-head logistic regression trained by ml/train.py and exported as JSON. Inference is a sparse dot product per
 * head, so it needs no native runtime. Throws [ModelFormatException] from [fromJson] if the artefact is missing or invalid;
 * callers fall back to the rule-based policy in that case.
 */
class LogRegClassifier private constructor(
    override val modelVersion: String,
    private val index: HashMap<String, Int>,
    private val heads: Map<String, Head>,
    override val thresholds: RoutingThresholds
) : NotificationClassifier {
    private class Head(val softmax: Boolean, val temperature: Double, val weights: Array<DoubleArray>, val bias: DoubleArray)

    val vocabularySize get() = index.size

    override fun classify(title: String?, text: String?, category: String?): SignalProbabilities {
        val idx = TextFeaturizer.featurize(title, text, category).mapNotNull { index[it] }.distinct().sorted()
        val v = if (idx.isEmpty()) 0.0 else 1.0 / sqrt(idx.size.toDouble())
        fun scores(h: Head): DoubleArray = DoubleArray(h.weights.size) { r ->
            var s = 0.0; val w = h.weights[r]; for (i in idx) s += w[i]
            (s * v + h.bias[r]) / h.temperature
        }
        fun sigmoid(h: Head) = 1.0 / (1.0 + exp(-scores(h)[0]))
        val imp = scores(heads.getValue("imp")).let { z -> val m = z.max(); val e = z.map { exp(it - m) }; val t = e.sum(); DoubleArray(z.size) { e[it] / t } }
        return SignalProbabilities(imp, sigmoid(heads.getValue("urg")), sigmoid(heads.getValue("dis")), sigmoid(heads.getValue("act")), modelVersion)
    }

    /**
     * Words present in this notification with the largest positive contribution to the "high importance" logit.
     * Only plain word features are reported, so explanations stay readable. Display only; never persisted.
     */
    fun topImportanceTerms(title: String?, text: String?, category: String?, n: Int): List<String> {
        val head = heads.getValue("imp")
        return TextFeaturizer.featurize(title, text, category).filter { it.startsWith("w:") && !it.endsWith("token") }
            .mapNotNull { f -> index[f]?.let { f.removePrefix("w:") to head.weights[2][it] - head.weights[0][it] } }
            .filter { it.second > 0.15 }.sortedByDescending { it.second }.take(n).map { it.first }
    }

    companion object {
        val REQUIRED_HEADS = mapOf("imp" to 3, "urg" to 1, "dis" to 1, "act" to 1)

        fun fromJson(json: String): LogRegClassifier = try { parse(json) } catch (e: ModelFormatException) { throw e } catch (e: Exception) {
            throw ModelFormatException("Model file is malformed (${e.javaClass.simpleName})")
        }

        private fun parse(json: String): LogRegClassifier {
            val m = try { Gson().fromJson(json, ModelJson::class.java) } catch (e: Exception) { throw ModelFormatException("Model file is not valid JSON") }
                ?: throw ModelFormatException("Model file is empty")
            if (m.format != "attune-logreg-v1") throw ModelFormatException("Unsupported model format: ${m.format}")
            val feats = m.features ?: throw ModelFormatException("Model has no features")
            val heads = m.heads ?: throw ModelFormatException("Model has no heads")
            val built = REQUIRED_HEADS.mapValues { (name, rows) ->
                val h = heads[name] ?: throw ModelFormatException("Missing head $name")
                if (h.weights.size != rows || h.bias.size != rows || h.weights.any { it.size != feats.size } || h.temperature <= 0.0)
                    throw ModelFormatException("Head $name has the wrong shape")
                Head(h.type == "softmax", h.temperature, h.weights.toTypedArray(), h.bias)
            }
            val index = HashMap<String, Int>(feats.size * 2); feats.forEachIndexed { i, f -> index[f] = i }
            return LogRegClassifier(m.modelVersion ?: "unknown", index, built, m.thresholds ?: RoutingThresholds())
        }
    }
}
