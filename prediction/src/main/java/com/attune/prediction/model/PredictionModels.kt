package com.attune.prediction.model

enum class RiskLevel { LOW, MED, HIGH }

/** Where the user was when the notification was reviewed. */
enum class SessionPhase { FOCUS, PAUSED_OR_BREAK, NO_SESSION }

data class NotificationContext(val packageName:String, val category:String, val title:String?, val text:String?, val postTime:Long, val groupCount:Int=1)
data class SessionContext(val phase:SessionPhase, val elapsedMinutes:Float, val remainingMinutes:Float)
data class UserObservation(val category:String, val reportedTimeAwayMin:Float, val reportedRecoveryMin:Float)

data class PredictionInput(
    val notification:NotificationContext,
    val session:SessionContext,
    val history:List<UserObservation>,
    val hourOfDay:Int
)

/** STARTING_ASSUMPTION = category prior or fixed rule; YOUR_DATA = derived from recorded observations; CONTEXT = current session/notification facts. */
enum class FactorSource { STARTING_ASSUMPTION, YOUR_DATA, CONTEXT }
data class PredictionFactor(val label:String, val detail:String, val source:FactorSource)

/**
 * timeAwayMin = estimated minutes spent in the other app.
 * recoveryMin = estimated minutes needed afterwards to get back into the task. These are separate quantities.
 */
data class Prediction(
    val risk:RiskLevel,
    val timeAwayMin:Float,
    val recoveryMin:Float,
    val explanation:String,
    val confidence:Float,
    val observationCount:Int,
    val delayScenario:String,
    val isEstimate:Boolean=true,
    val timeAwayLow:Float=timeAwayMin,
    val timeAwayHigh:Float=timeAwayMin,
    val recoveryLow:Float=recoveryMin,
    val recoveryHigh:Float=recoveryMin,
    val factors:List<PredictionFactor> = emptyList(),
    val isColdStart:Boolean=true,
    val modelVersion:String="baseline-v1"
) {
    val confidenceLabel:String get() = when { isColdStart -> "Cold start"; confidence >= 0.6f -> "Higher"; confidence >= 0.35f -> "Medium"; else -> "Low" }
}
