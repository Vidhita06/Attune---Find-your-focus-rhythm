package com.attune.app.capture

/** Minimal metadata captured from a posted notification. Text is kept only for display in the local queue. */
data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val previewText: String?,
    val postTimeMs: Long,
    val sbnKey: String,
    val androidCategory: String?,
    val isGroupSummary: Boolean
) { val dedupKey get() = "sbn:$sbnKey" }
