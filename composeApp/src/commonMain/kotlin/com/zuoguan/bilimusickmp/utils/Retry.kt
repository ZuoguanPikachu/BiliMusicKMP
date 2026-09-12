package com.zuoguan.bilimusickmp.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

/**
 * 以指数退避重试执行 [block]，直到成功或耗尽重试次数。
 *
 * [CancellationException] 与 [NoRetryException] 都直接向上抛出，
 * 不计入重试次数；单次退避上限为 5000 毫秒。
 *
 * @param times 重试次数上限（首次执行不计入），必须为非负数。
 * @param initialDelay 首次退避时长，单位毫秒。
 * @param factor 退避倍率，第 n 次退避为 initialDelay * factor^n。
 * @param retryOn 判定异常是否可重试；返回 false 时立即抛出原异常。
 * @return [block] 首次成功时的返回值。
 */
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
