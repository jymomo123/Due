package com.offline.due.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LifecycleResumeEffect(Unit) {
        val now = LocalDate.now()
        if (now != today) today = now
        onPauseOrDispose { }
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                val now = LocalDate.now()
                if (now != today) today = now
            }
        }
    }
    return today
}

@Composable
fun rememberNowTicking(enabled: Boolean, periodMillis: Long = 1000L): LocalDateTime =
    rememberNowState(enabled, periodMillis).value

@Composable
fun rememberNowState(enabled: Boolean, periodMillis: Long = 1000L): State<LocalDateTime> {
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(enabled, lifecycle, periodMillis) {
        if (!enabled) {
            now.value = LocalDateTime.now()
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now.value = LocalDateTime.now()
            while (true) {
                delay(periodMillis - System.currentTimeMillis() % periodMillis)
                now.value = LocalDateTime.now()
            }
        }
    }
    return now
}

val LocalNow = staticCompositionLocalOf<State<LocalDateTime>> {
    mutableStateOf(LocalDateTime.now())
}

