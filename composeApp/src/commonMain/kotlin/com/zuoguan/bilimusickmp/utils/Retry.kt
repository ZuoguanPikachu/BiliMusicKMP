package com.zuoguan.bilimusickmp.utils

import kotlinx.coroutines.delay
import kotlin.math.pow

suspend fun <T> retry(
    times: Int = 5,
    initialDelay: Long = 100L,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var lastException: Exception? = null

    repeat(times + 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt == times) throw e

            val delayMs = (initialDelay * factor.pow(attempt)).toLong().coerceAtMost(5000L)
            delay(delayMs)
        }
    }

    throw lastException ?: IllegalStateException("重试逻辑异常")
}