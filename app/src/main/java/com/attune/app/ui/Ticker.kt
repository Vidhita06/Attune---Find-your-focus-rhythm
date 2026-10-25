package com.attune.app.ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time periodically; used only to refresh displayed values, never to keep time. */
fun tickerFlow(periodMs: Long): Flow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(periodMs) } }
