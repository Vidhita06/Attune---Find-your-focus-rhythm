package com.attune.app

import com.attune.app.capture.*
import com.attune.app.data.DecisionRepository
import com.attune.core.model.FocusSession
import com.attune.core.model.NotificationDecisionRecord
import com.attune.core.model.SessionState
import com.attune.prediction.classifier.NotificationClassifier
import com.attune.prediction.classifier.RoutingThresholds
import com.attune.prediction.classifier.SignalProbabilities
import com.attune.prediction.decision.*
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import com.attune.prediction.policy.PolicySettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationProcessorTest {
    /** Deterministic model: "distracting, not important" unless the text says otherwise. */
    private class FakeModel(private val fail: Boolean = false) : NotificationClassifier {
        override val modelVersion = "fake"; override val thresholds = RoutingThresholds()
        override fun classify(title: String?, text: String?, category: String?): SignalProbabilities {
            if (fail) throw IllegalStateException("model broke")
            val important = text?.contains("deadline") == true
            return if (important) SignalProbabilities(doubleArrayOf(0.02, 0.08, 0.9), 0.8, 0.1, 0.9, modelVersion)
            else SignalProbabilities(doubleArrayOf(0.92, 0.06, 0.02), 0.03, 0.95, 0.05, modelVersion)
        }
    }

    private class FakeOps : ListenerOps {
        var connected = true
        val cancelled = mutableListOf<String>()
        var failCancel = false
        /** Whether Android reports the removal after cancelNotification() was requested. */
        var removalReported = true
        val active = mutableListOf<Pair<String, ActiveNotification>>() // groupKey -> notification
        override val isConnected get() = connected
        override fun cancel(key: String) {
            check(connected) { "called while disconnected" }
            if (failCancel) throw SecurityException("denied")
            cancelled += key; active.removeAll { it.second.key == key }
        }
        override suspend fun awaitRemoved(key: String) = removalReported && key in cancelled
        override fun activeInGroup(packageName: String, groupKey: String) = active.filter { it.first == groupKey }.map { it.second }
    }

    private inner class FakePorts(var session: FocusSession?, val engine: NotificationDecisionEngine, var policy: PolicySettings = basePolicy) : ProcessorPorts {
        var failSave = false; var failEvaluate = false; var removal = true; var evaluations = 0
        val stillVisible = mutableSetOf<String>()
        val decisions = mutableMapOf<String, NotificationDecisionRecord>()
        val queue = mutableMapOf<String, Triple<String, String?, String?>>() // dedupKey -> (id, title, text)
        val suppressed = mutableSetOf<String>()
        override suspend fun activeSession() = session
        override suspend fun evaluate(p: PostedNotification, phase: SessionPhase, session: FocusSession?): Pair<NotificationDecision, NotificationDecisionRecord?> {
            evaluations++
            if (failEvaluate) throw IllegalStateException("db locked")
            val d = engine.decide(DecisionInput(p.incoming, catOf(p.captured.packageName), phase, SenderKey.of(p.captured.packageName, p.captured.title)),
                DecisionSettings(true, policy), emptyList(), PersonalizationProfile.EMPTY, "com.attune.app")
            if (d.routing == Routing.IGNORE) return d to null
            val prev = decisions[p.captured.dedupKey]
            val rec = DecisionRepository.toRecord(d, prev?.id ?: "dec${decisions.size}", p.captured.dedupKey, p.captured.packageName, p.captured.appLabel,
                p.captured.postTimeMs, session?.id, phase, null, prev?.notificationId, false, (prev?.updateCount ?: 0) + 1)
            decisions[p.captured.dedupKey] = rec
            return d to rec
        }
        override suspend fun save(p: PostedNotification, d: NotificationDecision, decisionId: String, session: FocusSession, groupRepeated: Boolean): SaveResult {
            if (failSave) throw IllegalStateException("disk full")
            val prev = queue[p.captured.dedupKey]
            if (prev != null && prev.second == p.captured.title && prev.third == p.captured.previewText) return SaveResult.Saved(prev.first, duplicate = true)
            val id = prev?.first ?: "q${queue.size}"
            queue[p.captured.dedupKey] = Triple(id, p.captured.title, p.captured.previewText)
            return SaveResult.Saved(id, duplicate = false)
        }
        override suspend fun attach(decisionId: String, queueId: String) {}
        override suspend fun markSuppressed(queueId: String) { suppressed += queueId }
        override suspend fun markStillVisible(queueId: String) { stillVisible += queueId }
        override suspend fun removalEnabled() = removal
        override suspend fun groupRepeated() = false
        override suspend fun timeline(sessionId: String, type: String, appLabel: String, atMs: Long) {}
    }

    private val basePolicy = PolicySettings(setOf("messaging", "social", "short_video", "shopping", "promotion", "news", "email", "unknown"),
        emptySet(), emptySet(), allowCallsAndAlarms = true, allowOtp = true, filterPromotions = false)
    private fun catOf(pkg: String) = when (pkg) { "com.instagram.android" -> "social"; "com.whatsapp" -> "messaging"; else -> "unknown" }
    private val focusing = FocusSession("s1", 0, 25 * 60_000L, null, SessionState.FOCUS, plannedDurationMin = 25)

    private fun posted(pkg: String, title: String?, text: String?, key: String = "0|$pkg|1|null|10101", group: String? = null, summary: Boolean = false,
                       own: Boolean = false, ongoing: Boolean = false, priority: Boolean = false, postTime: Long = 1_000L) =
        PostedNotification(CapturedNotification(pkg, pkg.substringAfterLast('.'), title, text, postTime, key, null, summary),
            IncomingNotification(pkg, title, text, null, ongoing, summary, true, null, priority), group, own)

    private var clock = 1_000_000L
    private fun setup(session: FocusSession? = focusing, model: NotificationClassifier? = FakeModel()): Triple<NotificationProcessor, FakePorts, FakeOps> {
        val ports = FakePorts(session, NotificationDecisionEngine(model)); val ops = FakeOps(); clock = 1_000_000L
        return Triple(NotificationProcessor(ports, ops, { clock }), ports, ops)
    }

    @Test fun holdSavesFirstThenCancelsWithTheSbnKey() = runBlocking {
        val (proc, ports, ops) = setup()
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya and 8 others liked your photo", key = "0|com.instagram.android|7|tag|10101"))
        assertEquals(Routing.DEFER, o.routing); assertEquals(true, o.saved); assertEquals(CancelStatus.CONFIRMED, o.cancel)
        assertEquals(listOf("0|com.instagram.android|7|tag|10101"), ops.cancelled)
        assertEquals(1, ports.queue.size); assertTrue(ports.suppressed.isNotEmpty())
    }

    @Test fun failedSaveNeverCancels() = runBlocking {
        val (proc, ports, ops) = setup(); ports.failSave = true
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
        assertEquals(false, o.saved); assertEquals(CancelStatus.SKIPPED, o.cancel); assertTrue(ops.cancelled.isEmpty())
        assertTrue(o.reason.contains("left visible"))
    }

    @Test fun allowedNotificationIsNeverCancelledOrQueued() = runBlocking {
        val (proc, ports, ops) = setup()
        val o = proc.process(posted("com.whatsapp", "Mom", "Nani is in the hospital, please call me now"))
        assertEquals(Routing.DELIVER, o.routing); assertEquals(CancelStatus.NOT_NEEDED, o.cancel)
        assertTrue(ops.cancelled.isEmpty()); assertTrue(ports.queue.isEmpty())
    }

    @Test fun reviewIsQueuedButStaysVisible() = runBlocking {
        val (proc, ports, ops) = setup()
        val o = proc.process(posted("com.whatsapp", "Mom", "call me"))
        assertEquals(Routing.REVIEW, o.routing); assertEquals(true, o.saved); assertEquals(CancelStatus.NOT_NEEDED, o.cancel)
        assertEquals(1, ports.queue.size); assertTrue(ops.cancelled.isEmpty())
    }

    @Test fun pausedOrInactiveFocusKeepsNormalBehaviour() = runBlocking {
        for (s in listOf(focusing.copy(state = SessionState.PAUSED, breakStartMs = 1), focusing.copy(state = SessionState.BREAK, breakStartMs = 1), null)) {
            val (proc, ports, ops) = setup(session = s)
            val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
            assertEquals(CancelStatus.NOT_NEEDED, o.cancel); assertTrue(ops.cancelled.isEmpty()); assertTrue(ports.queue.isEmpty())
        }
    }

    @Test fun duplicateCallbacksCreateOneQueueItemAndKeepTheOriginalRemoved() = runBlocking {
        val (proc, ports, ops) = setup()
        val p = posted("com.instagram.android", "Instagram", "priya liked your photo and commented")
        val first = proc.process(p); val second = proc.process(p)
        assertEquals(1, ports.queue.size)
        assertFalse(first.duplicate); assertTrue(second.duplicate)
        assertEquals(CancelStatus.CONFIRMED, second.cancel) // a re-posted original is removed again, not left visible
        assertEquals(2, ops.cancelled.size)
        assertEquals(2, ports.decisions.values.single().updateCount) // still one decision record
    }

    @Test fun ownNotificationsAreNeverProcessed() = runBlocking {
        val (proc, ports, ops) = setup()
        val o = proc.process(posted("com.attune.app", "Focus session complete", "4 notifications waited for you", own = true))
        assertEquals(0, ports.evaluations); assertTrue(ops.cancelled.isEmpty()); assertTrue(ports.queue.isEmpty())
        assertEquals(CancelStatus.NOT_NEEDED, o.cancel)
    }

    @Test fun modelFailureNeverSilentlyHidesPersonalMessages() = runBlocking {
        val (proc, ports, ops) = setup(model = FakeModel(fail = true))
        val chat = proc.process(posted("com.whatsapp", "Rahul", "are you coming to the meeting later today"))
        assertEquals(Routing.REVIEW, chat.routing); assertTrue(ops.cancelled.isEmpty())
        val social = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "k2"))
        assertEquals(Routing.REVIEW, social.routing) // a model that threw on this input keeps every category visible
        assertTrue(ops.cancelled.isEmpty()); assertEquals(2, ports.queue.size)
    }

    @Test fun priorityContactAndUrgentMessagesAreNotCancelled() = runBlocking {
        val (proc, ports, ops) = setup()
        ports.policy = basePolicy.copy(priorityContacts = setOf("Rahul"))
        assertEquals(Routing.DELIVER, proc.process(posted("com.whatsapp", "Rahul", "lol look at this meme", key = "a")).routing)
        assertEquals(Routing.DELIVER, proc.process(posted("com.whatsapp", "Didi", "accident on the way home, call me", key = "b")).routing)
        assertEquals(Routing.DELIVER, proc.process(posted("com.whatsapp", "Prof", "project deadline moved to today 5pm, submit now", key = "c")).routing)
        assertTrue(ops.cancelled.isEmpty())
    }

    @Test fun disconnectedListenerSavesButNeverCallsCancel() = runBlocking {
        val (proc, ports, ops) = setup(); ops.connected = false
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
        assertEquals(true, o.saved); assertEquals(CancelStatus.SKIPPED, o.cancel); assertTrue(o.reason.contains("not connected"))
        assertTrue(ops.cancelled.isEmpty()); assertTrue(ports.suppressed.isEmpty()); assertEquals(1, ports.stillVisible.size)
    }

    @Test fun cancelFailureIsReportedAndNotMarkedSuppressed() = runBlocking {
        val (proc, ports, ops) = setup(); ops.failCancel = true
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
        assertEquals(CancelStatus.FAILED, o.cancel); assertTrue(ports.suppressed.isEmpty()); assertEquals(1, ports.queue.size); assertEquals(1, ports.stillVisible.size)
    }

    @Test fun requestedButUnconfirmedRemovalIsNotCountedAsSuppressed() = runBlocking {
        val (proc, ports, ops) = setup(); ops.removalReported = false
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
        assertEquals(1, ops.cancelled.size) // it was requested ...
        assertEquals(CancelStatus.UNCONFIRMED, o.cancel); assertTrue(o.reason.contains("did not confirm"))
        assertTrue(ports.suppressed.isEmpty()); assertEquals(1, ports.stillVisible.size) // ... but never reported as suppressed
    }

    @Test fun reconnectedListenerHoldsAgain() = runBlocking {
        val (proc, _, ops) = setup()
        ops.connected = false
        assertEquals(CancelStatus.SKIPPED, proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "a")).cancel)
        ops.connected = true
        assertEquals(CancelStatus.CONFIRMED, proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "b")).cancel)
        assertEquals(listOf("b"), ops.cancelled)
    }

    @Test fun shortOrAmbiguousMessagesStayVisibleForReview() = runBlocking {
        val (proc, ports, ops) = setup()
        for ((i, t) in listOf("ok", "hi", "\uD83D\uDC4D", "https://example.com/abc").withIndex()) {
            val o = proc.process(posted("com.whatsapp", "Aman", t, key = "k$i"))
            assertEquals(t, Routing.REVIEW, o.routing); assertEquals(t, CancelStatus.NOT_NEEDED, o.cancel)
        }
        assertTrue(ops.cancelled.isEmpty()); assertEquals(4, ports.queue.size)
    }

    @Test fun emptyOrRedactedContentIsNeverCancelled() = runBlocking {
        val (proc, ports, ops) = setup(model = null)
        for ((i, t) in listOf<String?>(null, "", "Sensitive notification content hidden", "New message").withIndex()) {
            val o = proc.process(posted("com.whatsapp", "Aman", t, key = "k$i"))
            assertEquals(t, Routing.REVIEW, o.routing)
        }
        assertTrue(ops.cancelled.isEmpty()); assertEquals(4, ports.queue.size)
    }

    @Test fun possiblyUrgentWordingIsKeptVisible() = runBlocking {
        val (proc, _, ops) = setup()
        val o = proc.process(posted("com.whatsapp", "Aman", "call me now, need to talk about tomorrow"))
        assertEquals(Routing.REVIEW, o.routing); assertTrue(ops.cancelled.isEmpty())
        assertEquals(Routing.REVIEW, proc.process(posted("com.whatsapp", "Aman", "jaldi call karo yaar kuch bataana hai", key = "z")).routing)
    }

    @Test fun updatedNotificationIsReEvaluatedAndHeldOrReleasedAccordingToNewContent() = runBlocking {
        val (proc, ports, ops) = setup()
        val key = "0|com.whatsapp|1|chat|10101"
        val first = proc.process(posted("com.whatsapp", "Group", "lol that meme is so funny yaar", key = key, postTime = 1_000))
        assertEquals(Routing.DEFER, first.routing)
        clock += 10 * 60_000L // well outside the burst / follow-up windows
        val updated = proc.process(posted("com.whatsapp", "Group", "haha another meme, this one is even better", key = key, postTime = 2_000))
        assertEquals(Routing.DEFER, updated.routing); assertFalse(updated.duplicate) // changed content updates the SAME queue item
        assertEquals(1, ports.queue.size); assertEquals(listOf(key, key), ops.cancelled)
        clock += 10 * 60_000L
        val urgent = proc.process(posted("com.whatsapp", "Group", "project deadline moved to today 5pm, submit now", key = key, postTime = 3_000))
        assertEquals(Routing.DELIVER, urgent.routing); assertEquals(2, ops.cancelled.size) // an important update is not cancelled
    }

    @Test fun burstFromOneConversationStopsBeingHeld() = runBlocking {
        val (proc, _, ops) = setup()
        val r = listOf("a", "b", "c", "d").mapIndexed { i, k -> proc.process(posted("com.whatsapp", "Aman", "lol that meme is so funny yaar", key = k, postTime = 1_000L + i)) }
        // the 2nd message follows one Attune just held (follow-up rule); everything after that is a burst
        assertEquals(listOf(Routing.DEFER, Routing.REVIEW, Routing.REVIEW, Routing.REVIEW), r.map { it.routing })
        assertEquals(listOf("a"), ops.cancelled)
        // an unrelated conversation is unaffected, and repeated callbacks of one post don't add to a burst
        assertEquals(Routing.DEFER, proc.process(posted("com.whatsapp", "Priya", "lol that meme is so funny yaar", key = "p")).routing)
        val (proc2, _, ops2) = setup()
        repeat(5) { proc2.process(posted("com.whatsapp", "Aman", "lol that meme is so funny yaar", key = "same", postTime = 5_000)) }
        assertEquals(5, ops2.cancelled.size)
    }

    @Test fun priorityConversationIsAllowedAndMissingMetadataFallsBack() = runBlocking {
        val (proc, ports, ops) = setup()
        val prio = proc.process(posted("com.whatsapp", "Aman", "lol that meme is so funny yaar", key = "a", priority = true))
        assertEquals(Routing.DELIVER, prio.routing); assertTrue(ports.queue.isEmpty()); assertTrue(ops.cancelled.isEmpty())
        assertEquals(Routing.DEFER, proc.process(posted("com.whatsapp", "Aman", "lol that meme is so funny yaar", key = "b", priority = false)).routing)
    }

    @Test fun importantMessagesDoNotDependOnTheQueueOrTheDatabase() = runBlocking {
        val (proc, ports, ops) = setup(); ports.failSave = true; ports.policy = basePolicy.copy(priorityContacts = setOf("Rahul"))
        val o = proc.process(posted("com.whatsapp", "Rahul", "lol look at this meme"))
        assertEquals(Routing.DELIVER, o.routing); assertTrue(ops.cancelled.isEmpty())
        ports.failEvaluate = true
        val bad = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "x"))
        assertEquals(CancelStatus.SKIPPED, bad.cancel); assertTrue(ops.cancelled.isEmpty()); assertTrue(bad.reason.contains("left visible"))
    }

    @Test fun focusPauseResumeAndCompletionAreFollowedOnEveryCallback() = runBlocking {
        val (proc, ports, ops) = setup()
        fun post(k: String) = posted("com.instagram.android", "Instagram $k", "priya liked your photo and commented", key = k) // distinct titles: no burst
        assertEquals(CancelStatus.CONFIRMED, proc.process(post("1")).cancel)                      // focusing
        ports.session = focusing.copy(state = SessionState.PAUSED, breakStartMs = 1)
        assertEquals(CancelStatus.NOT_NEEDED, proc.process(post("2")).cancel)                     // paused: reaches the user
        ports.session = focusing
        assertEquals(CancelStatus.CONFIRMED, proc.process(post("3")).cancel)                      // resumed
        ports.session = focusing.copy(state = SessionState.COMPLETED, actualEndMs = 5)
        assertEquals("inactive", proc.process(post("4")).focus)                                   // completed: normal again
        assertEquals(listOf("1", "3"), ops.cancelled)
    }

    @Test fun removalSettingOffKeepsOriginal() = runBlocking {
        val (proc, ports, ops) = setup(); ports.removal = false
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
        assertEquals(CancelStatus.SKIPPED, o.cancel); assertTrue(ops.cancelled.isEmpty()); assertEquals(1, ports.queue.size)
    }

    @Test fun groupSummaryIsRemovedOnlyWhenNoVisibleChildRemains() = runBlocking {
        val (proc, _, ops) = setup()
        val g = "0|com.instagram.android|g:likes"
        ops.active += g to ActiveNotification("child1", false, true, false)
        ops.active += g to ActiveNotification("summary", true, true, false)
        val o = proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "child1", group = g))
        assertEquals(1, o.summariesCancelled); assertEquals(listOf("child1", "summary"), ops.cancelled)

        // Another group with an important child still visible: the summary must stay.
        val (proc2, _, ops2) = setup()
        val g2 = "0|com.whatsapp|g:chats"
        ops2.active += g2 to ActiveNotification("important", false, true, false)
        ops2.active += g2 to ActiveNotification("child2", false, true, false)
        ops2.active += g2 to ActiveNotification("summary2", true, true, false)
        proc2.process(posted("com.whatsapp", "Aman", "lol so funny this meme yaar", key = "child2", group = g2))
        assertEquals(listOf("child2"), ops2.cancelled)
        // A summary posted later for a held group with nothing visible is removed; outside a held group it is left alone.
        ops2.active.removeAll { it.second.key == "important" }
        val s = proc2.process(posted("com.whatsapp", "WhatsApp", "2 messages", key = "summary2", group = g2, summary = true))
        assertEquals(1, s.summariesCancelled)
        val other = proc2.process(posted("com.whatsapp", "WhatsApp", "3 messages", key = "summary3", group = "unrelated", summary = true))
        assertEquals(CancelStatus.NOT_NEEDED, other.cancel)
    }

    @Test fun followUpOutsideTheWindowIsHeldAgainAndWindowIsConfigurable() = runBlocking {
        val (proc, _, ops) = setup()
        assertEquals(Routing.DEFER, proc.process(posted("com.whatsapp", "Aman", "lol that meme is so funny yaar", key = "a")).routing)
        clock += 6 * 60_000L // after the 5 minute follow-up window and the 2 minute burst window
        assertEquals(Routing.DEFER, proc.process(posted("com.whatsapp", "Aman", "lol that meme is so funny yaar", key = "b", postTime = 2_000)).routing)
        assertEquals(listOf("a", "b"), ops.cancelled)
    }

    @Test fun heldPersistedBeforeCancelAndOrderIsRecorded() = runBlocking {
        val order = mutableListOf<String>()
        val (_, ports, ops) = setup()
        val tracing = object : ProcessorPorts by ports {
            override suspend fun save(p: PostedNotification, d: NotificationDecision, decisionId: String, session: FocusSession, groupRepeated: Boolean): SaveResult { order += "save"; return ports.save(p, d, decisionId, session, groupRepeated) }
            override suspend fun markSuppressed(queueId: String) { order += "suppressed"; ports.markSuppressed(queueId) }
        }
        val tracingOps = object : ListenerOps by ops { override fun cancel(key: String) { order += "cancel"; ops.cancel(key) } }
        NotificationProcessor(tracing, tracingOps, { clock }).process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented"))
        assertEquals(listOf("save", "cancel", "suppressed"), order) // persisted first; marked suppressed only after Android confirmed
    }

    @Test fun diagnosticsDescribeEveryOutcomeWithoutContent() = runBlocking {
        val (proc, _, ops) = setup()
        val secret = "my secret plan with code 482913 at http://evil.example/path?token=abc"
        val outcomes = listOf(
            proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "1")),
            proc.process(posted("com.whatsapp", "Aman", secret, key = "2")),
            proc.process(posted("com.whatsapp", "Aman", "ok", key = "3")))
        ops.connected = false
        val skipped = proc.process(posted("com.instagram.android", "Other chat", "priya liked your photo and commented", key = "4"))
        for (o in outcomes + skipped) {
            val line = ListenerDiagnostics.line(o)
            for (leak in listOf("482913", "evil.example", "token=abc", "secret plan", "priya liked")) assertFalse("$leak in: $line", leak in line || leak in o.reason)
        }
        assertEquals(CancelStatus.SKIPPED, skipped.cancel)
    }

    // ---- real-time suppression: diagnostic states ----

    @Test fun everyOutcomeHasAnAccurateDiagnosticState() = runBlocking {
        val (proc, ports, ops) = setup()
        fun ig(k: String, t: String = "Instagram $k") = posted("com.instagram.android", t, "priya liked your photo and commented", key = k)
        assertEquals(HoldState.CANCELLATION_CONFIRMED, proc.process(ig("a")).state)                       // held, persisted, removal confirmed
        assertEquals(HoldState.ALLOWED, proc.process(posted("com.whatsapp", "Mom", "Nani is in the hospital, please call", key = "b")).state)
        assertEquals(HoldState.REVIEW_VISIBLE, proc.process(posted("com.whatsapp", "Aman", "ok", key = "c")).state)
        ports.failSave = true
        assertEquals(HoldState.QUEUE_PERSIST_FAILED, proc.process(ig("d")).state); ports.failSave = false
        ops.failCancel = true
        assertEquals(HoldState.CANCELLATION_FAILED, proc.process(ig("e")).state); ops.failCancel = false
        ops.removalReported = false
        assertEquals(HoldState.CANCELLATION_ATTEMPTED, proc.process(ig("f")).state); ops.removalReported = true
        ports.removal = false
        assertEquals(HoldState.HOLD_PERSISTED, proc.process(ig("g")).state); ports.removal = true
        ops.connected = false
        assertEquals(HoldState.LISTENER_DISCONNECTED, proc.process(ig("h")).state); ops.connected = true
        ports.session = null
        assertEquals(HoldState.FOCUS_INACTIVE, proc.process(ig("i")).state)
    }

    @Test fun startingASessionTakesEffectOnTheNextCallbackWithoutRestartingTheListener() = runBlocking {
        val (proc, ports, ops) = setup(session = null)
        fun ig(k: String) = posted("com.instagram.android", "Instagram $k", "priya liked your photo and commented", key = k)
        assertTrue(proc.process(ig("1")).let { it.cancel == CancelStatus.NOT_NEEDED && it.focus == "inactive" })      // no session: normal behaviour
        ports.session = focusing                                                                                      // user taps Start
        assertEquals(CancelStatus.CONFIRMED, proc.process(ig("2")).cancel)                                            // held immediately
        ports.session = focusing.copy(state = SessionState.PAUSED, breakStartMs = 1)
        assertEquals("paused", proc.process(ig("3")).focus)
        ports.session = focusing.copy(state = SessionState.ENDED_EARLY, actualEndMs = 9)                              // user ends it
        assertEquals("inactive", proc.process(ig("4")).focus)
        assertEquals(listOf("2"), ops.cancelled)
    }

    @Test fun aRealCallbackBecomesAQueueRecordWithItsOwnContent() = runBlocking {
        val (proc, ports, _) = setup()
        proc.process(posted("com.instagram.android", "Instagram", "priya liked your photo and commented", key = "real-1"))
        val row = ports.queue.values.single()
        assertEquals("priya liked your photo and commented", row.third); assertEquals("Instagram", row.second) // exactly what the callback carried, nothing seeded
        assertTrue(ports.queue.keys.single().startsWith("sbn:")) // keyed by the real StatusBarNotification key, never a demo id
    }
}
