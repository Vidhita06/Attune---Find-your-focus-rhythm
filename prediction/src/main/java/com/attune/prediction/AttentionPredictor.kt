package com.attune.prediction
import com.attune.prediction.model.Prediction
import com.attune.prediction.model.PredictionInput

/**
 * Replaceable prediction boundary. The prototype ships [PredictionEngine] (a transparent, deterministic baseline).
 * A trained model can implement this interface later without changing the app layer.
 */
interface AttentionPredictor {
    val modelVersion: String
    fun predict(input: PredictionInput): Prediction
}
