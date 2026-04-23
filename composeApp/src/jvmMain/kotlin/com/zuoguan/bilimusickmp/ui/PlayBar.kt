package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
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
    onLyricsClick: () -> Unit,
    viewModel: PlayBarViewModel = koinInject()
) {
    Surface(
        tonalElevation = 2.dp
    ) {
        val state by viewModel.uiState.collectAsState()
        val currentTrack = state.currentTrack

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentTrack != null) {
                when (val resource = asyncPainterResource(convertImageUrl(currentTrack.pic, 128, 128))) {
                    is Resource.Loading -> { }
                    is Resource.Success -> {
                        val painter: Painter = resource.value
                        Image(
                            painter,
                            contentDescription = currentTrack.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    is Resource.Failure -> {
                        CoverPlaceholder()
                    }
                }
            } else {
                CoverPlaceholder()
            }

            Spacer(modifier = Modifier.width(28.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (currentTrack != null) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "未播放",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (currentTrack != null) {
                    Text(
                        text = currentTrack.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(28.dp))

            Column(
                modifier = Modifier.weight(2f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = viewModel::playPrevious) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null)
                    }
                    IconButton(onClick = {
                        if (state.playbackState == PlaybackState.Playing)
                            viewModel.pause()
                        else
                            viewModel.resume()
                    }) {
                        Icon(
                            if (state.playbackState == PlaybackState.Playing)
                                Icons.Default.Pause
                            else
                                Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = viewModel::playNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                    }
                }

                Slider(
                    value = state.position,
                    onValueChange = viewModel::seek,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .offset(y = 2.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(3.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End
            ) {
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

                IconButton(
                    onClick = onLyricsClick
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = "歌词")
                }
            }

        }
    }
}

