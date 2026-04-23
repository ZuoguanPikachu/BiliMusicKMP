package com.zuoguan.bilimusickmp.utils

import java.util.Base64

actual fun base64Encode(bytes: ByteArray): String {
    return Base64.getEncoder().encodeToString(bytes)
}