package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.utils.formatBytes
import com.zuoguan.bilimusickmp.utils.isMobileUi
import com.zuoguan.bilimusickmp.vm.CacheUiState

/**
 * 设置页的「缓存」区块。
 *
 * 两个开关分别对应「播放时自动缓存」与「仅在 Wi-Fi 下缓存」，改动立即生效、无需保存；
 * 未开启缓存时，Wi-Fi 开关置灰——它单独开着没有意义，反而会让人误以为缓存在跑。
 *
 * 这两个开关都只存在本机，不随云同步跑到其他设备：每台设备的存储容量与流量套餐不同，
 * 同步过去只会互相覆盖。这里只管应用自己的缓存，不管下载到音乐目录的文件。
 */
@Composable
fun CacheSettingsSection(
    state: CacheUiState,
    onEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onClearCache: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        CacheSwitchRow(
            icon = Icons.Default.Cached,
            title = "播放时自动缓存",
            subtitle = "边听边存一份，下次播放直接用本地文件",
            checked = state.enabled,
            onCheckedChange = onEnabledChange
        )

        // 桌面端没有「移动网络」这一概念（也判断不出是不是插着手机热点），
        // 这个开关只在移动端有意义，隐藏掉比给一个永远不生效的开关更清楚。
        if (isMobileUi) {
            CacheSwitchRow(
                icon = Icons.Default.Wifi,
                title = "仅在 Wi-Fi 下缓存",
                subtitle = if (state.enabled) {
                    "移动网络下不自动缓存，避免消耗流量"
                } else {
                    "开启自动缓存后可设置"
                },
                checked = state.wifiOnly,
                enabled = state.enabled,
                onCheckedChange = onWifiOnlyChange
            )
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Cached,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "缓存占用", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = cacheSummary(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            // 没有缓存时按钮置灰，避免点了什么也没发生
            OutlinedButton(
                onClick = onClearCache,
                enabled = state.cacheSizeBytes > 0
            ) {
                Text("清理缓存")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** 一行开关：图标 + 标题/说明 + 右侧 Switch。 */
@Composable
private fun CacheSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/** 缓存区的一行摘要：占用大小，正在缓存时补一句。 */
private fun cacheSummary(state: CacheUiState): String {
    val summary = formatBytes(state.cacheSizeBytes)
    return if (state.activeTransferCount > 0) {
        "$summary · ${state.activeTransferCount} 首缓存中"
    } else {
        summary
    }
}
