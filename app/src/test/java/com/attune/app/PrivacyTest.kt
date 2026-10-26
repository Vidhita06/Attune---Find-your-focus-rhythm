package com.attune.app

import com.attune.app.data.QueueRepository
import com.attune.prediction.classifier.SafetyRules
import org.junit.Assert.*
import org.junit.Test

/** Data-minimisation rules that don't need a database: what counts as sensitive and what may be stored for it. */
class PrivacyTest {
    @Test fun codesSecurityAlertsAndMoneyAreSensitive() {
        assertTrue(SafetyRules.isSensitive("Bank", "Your OTP is 482913. Do not share it.", "finance"))
        assertTrue(SafetyRules.isSensitive("Google", "New sign-in to your account from Pune", "email"))
        assertTrue(SafetyRules.isSensitive("HDFC", "Rs 4,200 debited from a/c XX1234, avl bal 12,000", null))
        assertFalse(SafetyRules.isSensitive("Aman", "see you at the cafe tomorrow evening", "messaging"))
    }

    @Test fun sensitiveTextIsNeverStorable() {
        assertNull(QueueRepository.storable(true, "Your OTP is 482913"))
        assertNull(QueueRepository.storable(true, null))
        assertEquals("see you tomorrow", QueueRepository.storable(false, "see you tomorrow"))
    }

    @Test fun retentionIsTwentyFourHoursWithAHardBackstop() {
        assertEquals(24, QueueRepository.TEXT_RETENTION_HOURS)
        assertEquals(7, QueueRepository.MAX_PENDING_TEXT_DAYS)
    }

    @Test fun demoDataIsOffByDefaultAndNeverShownAsRealQueueItems() {
        assertFalse(com.attune.app.data.AttuneSettings().demoEnabled)
        fun item(id: String, demo: Boolean) = com.attune.core.model.QueuedNotification(id, "s", "p", "messaging", "T", "x", "d$id", 1, isDemo = demo)
        val all = listOf(item("real", false), item("demo", true))
        assertEquals(listOf("real"), QueueRepository.visibleItems(all, demoEnabled = false).map { it.id })
        assertEquals(2, QueueRepository.visibleItems(all, demoEnabled = true).size)
        assertTrue(QueueRepository.visibleItems(emptyList(), false).isEmpty()) // a fresh install has an honestly empty queue
    }
}
