package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource

/** 手机端搜索结果条目：整行可点播放，右侧加号进入新建。 */
@Composable
fun MobileSearchResultItem(
    item: SearchResult,
    onItemClick: (SearchResult) -> Unit,
    onAddButtonClick: (SearchResult) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { onItemClick(item) },
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(12.dp))
            ) {

                if (item.pic.isBlank()) {
                    CoverPlaceholder()
                } else when (val resource = asyncPainterResource(convertImageUrl(item.pic, 320, 200))) {
                    is Resource.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is Resource.Success -> {
                        Image(
                            painter = resource.value,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    is Resource.Failure -> {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray)) {
                            Text("加载失败", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }

                Text(
                    text = item.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ){
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)

                    )

                    IconButton(
                        onClick = { onAddButtonClick(item) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }

            }
        }
    }
}

/** 平板/PC 端搜索结果卡片：整卡可点播放，加号进入新建。 */
@Composable
fun PcSearchResultItem(
    item: SearchResult,
    onItemClick: (SearchResult) -> Unit,
    onAddButtonClick: (SearchResult) -> Unit
) {
    Card(
        onClick = { onItemClick(item) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            // 点击区域交给 Card，避免只有内层内容可点（卡片 padding 点不到）
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
            ) {
                if (item.pic.isBlank()) {
                    CoverPlaceholder()
                } else when (val resource = asyncPainterResource(convertImageUrl(item.pic, 512, 320))) {
                    is Resource.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is Resource.Success -> {
                        val painter: Painter = resource.value
                        Image(
                            painter,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    is Resource.Failure -> {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray)) {
                            Text("加载失败", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }

                Text(
                    text = item.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ){
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(
                    onClick = { onAddButtonClick(item) },
                ){
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }

        }
    }
}