package com.zuoguan.bilimusickmp.utils

import android.util.Base64

actual fun base64Encode(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}