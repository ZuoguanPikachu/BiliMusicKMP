package com.zuoguan.bilimusickmp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 当前是否连着 Wi-Fi（或以太网）。
 *
 * 「仅 Wi-Fi 下缓存」用的是它：移动网络返回 false，避免在流量下自动缓存整首歌。
 * 取不到网络信息时按 false 处理——宁可这次不缓存，也不要在不确定的情况下耗用户流量。
 */
fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false

    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
