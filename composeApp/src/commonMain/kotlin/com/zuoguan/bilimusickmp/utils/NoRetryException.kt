package com.zuoguan.bilimusickmp.utils

/** 标记不可重试的异常；[retry] 捕获后直接向上抛出，不计入重试次数。 */
class NoRetryException(message: String) : Exception(message)