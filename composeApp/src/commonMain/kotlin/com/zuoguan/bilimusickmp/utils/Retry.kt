package com.zuoguan.bilimusickmp.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

suspend fun <T> retry(
    times: Int = 5,
    initialDelay: Long = 100L,
    factor: Double = 2.0,
    retryOn: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): T {
    require(times >= 0) { "times 不能为负: $times" }

    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            // 协程取消不是"失败"，必须原样抛出，否则会破坏结构化并发
            throw e
        } catch (e: NoRetryException) {
            throw e
        } catch (e: Exception) {
            if (attempt >= times || !retryOn(e)) throw e

            val delayMs = (initialDelay * factor.pow(attempt)).toLong().coerceAtMost(5000L)
            currentCoroutineContext().ensureActive()
            delay(delayMs.milliseconds)
            attempt++
        }
    }
}
