package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import com.zuoguan.bilimusickmp.vm.PlayBarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayBar(
    onClick: () -> Unit,
    viewModel: PlayBarViewModel = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
    val currentTrack = state.currentTrack
    val isPlaying = state.playbackState == PlaybackState.Playing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false
            ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentTrack != null) {
                when (val resource = asyncPainterResource(convertImageUrl(currentTrack.pic, 128, 128))) {
                    is Resource.Loading -> {
                        CoverPlaceholder()
                    }
                    is Resource.Success -> {
                        Image(
                            painter = resource.value,
                            contentDescription = currentTrack.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    is Resource.Failure -> {
                        CoverPlaceholder()
                    }
                }
            } else {
                CoverPlaceholder()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentTrack?.title ?: "未播放",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (currentTrack != null){
                    Text(
                        text = currentTrack.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {
                    if (isPlaying) viewModel.pause() else viewModel.resume()
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                )
            }

            IconButton(onClick = viewModel::togglePlayMode) {
                Icon(
                    imageVector = when (state.playMode) {
                        PlayMode.SEQUENTIAL -> Icons.Default.Repeat
                        PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                    },
                    contentDescription = "Play Mode"
                )
            }
        }
    }
}