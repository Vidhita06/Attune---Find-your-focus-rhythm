package com.attune.app.data
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.attune.app.capture.FocusListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

enum class AccessStatus { NOT_GRANTED, GRANTED_WAITING, CONNECTED }

/** Notification access can only be granted by the user in Android settings; Attune never tries to grant it itself. */
@Singleton
class NotificationAccessMonitor @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val granted = MutableStateFlow(isGranted())
    private val connected = MutableStateFlow(false)
    val status: StateFlow<AccessStatus> = combine(granted, connected) { g, c ->
        when { !g -> AccessStatus.NOT_GRANTED; c -> AccessStatus.CONNECTED; else -> AccessStatus.GRANTED_WAITING }
    }.stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), SharingStarted.Eagerly, AccessStatus.NOT_GRANTED)

    fun isGranted() = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    fun refresh() { granted.value = isGranted(); if (!granted.value) connected.value = false }
    fun onListenerConnected(c: Boolean) { connected.value = c; refresh() }

    fun settingsIntent(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(ctx, FocusListenerService::class.java).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    /** Opens the access screen, falling back to the general listener list if the detail page isn't available. */
    fun openSettings(): Boolean = runCatching { ctx.startActivity(settingsIntent()); true }.getOrElse {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
    fun openAppDetails(): Boolean = runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
}
