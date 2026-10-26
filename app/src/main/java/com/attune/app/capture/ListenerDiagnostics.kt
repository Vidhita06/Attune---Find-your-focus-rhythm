package com.attune.app.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory, privacy-safe record of what the notification listener did (last [MAX] callbacks). Entries contain only
 * the app name/package, focus state, routing, save and cancel outcomes and a generic reason — never titles, text, OTPs,
 * URLs or senders. Nothing is persisted; it resets when the process restarts.
 */
object ListenerDiagnostics {
    const val MAX = 40
    data class Snapshot(val connected: Boolean = false, val lastChangeMs: Long = 0, val events: List<ProcessOutcome> = emptyList(),
                        val held: Int = 0, val cancelAttempts: Int = 0, val cancelFailures: Int = 0, val saveFailures: Int = 0, val cancelUnconfirmed: Int = 0,
                        /** Callbacks received from Android since this process started. 0 while connected means Android is not delivering notifications to Attune. */
                        val callbacks: Int = 0, val lastCallbackMs: Long = 0)

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun onConnection(connected: Boolean, atMs: Long = System.currentTimeMillis()) = _state.update { it.copy(connected = connected, lastChangeMs = atMs) }

    fun onCallback(atMs: Long = System.currentTimeMillis()) = _state.update { it.copy(callbacks = it.callbacks + 1, lastCallbackMs = atMs) }

    fun record(o: ProcessOutcome) = _state.update { s ->
        s.copy(events = (listOf(o) + s.events).take(MAX),
            held = s.held + if (o.saved == true && o.routing?.name in setOf("DEFER", "FILTER") && !o.duplicate) 1 else 0,
            cancelAttempts = s.cancelAttempts + if (o.cancel == CancelStatus.CONFIRMED) 1 else 0,
            cancelUnconfirmed = s.cancelUnconfirmed + if (o.cancel == CancelStatus.UNCONFIRMED) 1 else 0,
            cancelFailures = s.cancelFailures + if (o.cancel == CancelStatus.FAILED) 1 else 0,
            saveFailures = s.saveFailures + if (o.saved == false) 1 else 0)
    }

    /** One log line without private content, e.g. "com.whatsapp focus=active decision=HOLD saved=true cancel=ATTEMPTED (Held in queue…)". */
    fun line(o: ProcessOutcome) = "${o.packageName} state=${o.state} focus=${o.focus} decision=${label(o.routing)} saved=${o.saved ?: "-"} cancel=${o.cancel}${if (o.summariesCancelled > 0) " summaries=${o.summariesCancelled}" else ""} (${o.reason})"

    fun label(r: com.attune.prediction.decision.Routing?) = when (r?.name) { "DEFER" -> "HOLD"; "DELIVER" -> "ALLOW"; null -> "—"; else -> r.name }
}
