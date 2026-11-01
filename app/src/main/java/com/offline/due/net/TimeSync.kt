package com.offline.due.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class TimeProbe(
    val serverMillis: Long,
    val localMillis: Long,
) {
    val skewMillis: Long get() = serverMillis - localMillis

    val significant: Boolean get() = skewMillis > 5 * 60_000L || skewMillis < -5 * 60_000L
}

object TimeSync {

    private val ENDPOINTS = listOf(
        "https://www.baidu.com",
        "https://www.jd.com",
    )

    private const val TIMEOUT_MS = 4000

    private const val SANITY_MS = 30L * 24L * 60L * 60L * 1000L

    suspend fun probe(): TimeProbe? = withContext(Dispatchers.IO) {
        for (endpoint in ENDPOINTS) {
            val result = runCatching { probeOnce(endpoint) }.getOrNull()
            if (result != null) return@withContext result
        }
        null
    }

    private fun probeOnce(endpoint: String): TimeProbe? {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "due-time-check")
        }
        return try {
            conn.connect()
// Date 头缺失时返回 0，这里必须判掉
            val serverMillis = conn.getHeaderFieldDate("Date", 0L)
            if (serverMillis <= 0L) return null
            val localMillis = System.currentTimeMillis()
            if (serverMillis - localMillis > SANITY_MS ||
                localMillis - serverMillis > SANITY_MS
            ) {
                return null
            }
            TimeProbe(serverMillis = serverMillis, localMillis = localMillis)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    fun describe(skewMillis: Long): String {
        val abs = if (skewMillis < 0) -skewMillis else skewMillis
        if (abs < 2_000L) return "准确"
        val text = when {
            abs < 60_000L -> "${abs / 1000} 秒"
            abs < 3_600_000L -> "${abs / 60_000} 分 ${abs % 60_000 / 1000} 秒"
            abs < 86_400_000L -> "${abs / 3_600_000} 小时 ${abs % 3_600_000 / 60_000} 分"
            else -> "${abs / 86_400_000} 天"
        }
        return if (skewMillis > 0) "本机慢 $text" else "本机快 $text"
    }
}

