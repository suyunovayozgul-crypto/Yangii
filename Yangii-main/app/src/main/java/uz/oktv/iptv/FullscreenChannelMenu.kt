package uz.oktv.iptv

import androidx.compose.animation.AnimatedVisibility
import coil.compose.AsyncImage
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BoxScope.FullscreenChannelMenu(
    visible: Boolean,
    menuMode: String,
    filteredChannels: List<M3UChannel>,
    activeCategories: List<String>,
    categoryChannelCounts: Map<String, Int>,
    favorites: List<Int>,
    selectedChannelIndex: Int,
    fullscreenChannelIndex: Int,
    selectedCategoryIndex: Int,
    fullscreenCategoryIndex: Int,
    selectedCategoryName: String
) {
    if (!visible) return

    val index = if (menuMode == "CHANNELS") fullscreenChannelIndex else fullscreenCategoryIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, index - 2))

    LaunchedEffect(
        menuMode,
        fullscreenChannelIndex,
        fullscreenCategoryIndex
    ) {
        val scrollIndex = if (menuMode == "CHANNELS") {
            // Боковое меню: страницы по 20 каналов
            // 1-20 -> 21-40 -> 41-60 -> ...
            (fullscreenChannelIndex / 20) * 20
        } else {
            fullscreenCategoryIndex
        }

        if (scrollIndex >= 0) {
            try {
                listState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {
            }
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(Color(0xCC050811))
                .border(
                    width = 1.dp,
                    color = Color(0xFF38BDF8).copy(alpha = 0.45f)
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 20.dp
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = if (menuMode == "CHANNELS")
                        selectedCategoryName.ifEmpty { "КАНАЛЫ" }
                    else
                        "КАТЕГОРИИ",
                    color = Color(0xFFFF9800),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (menuMode == "CHANNELS") {

                        itemsIndexed(
                            filteredChannels,
                            key = { _, channel -> channel.id }
                        ) { index, channel ->

                            val focused =
                                index == fullscreenChannelIndex

                            val playing =
                                index == selectedChannelIndex

                            val favorite =
                                favorites.contains(channel.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            focused -> Color(0xFF164E63)
                                            playing -> Color(0x6638BDF8)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (focused) 1.5.dp else 0.dp,
                                        color = if (focused)
                                            Color(0xFF38BDF8)
                                        else
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(
                                        horizontal = 10.dp,
                                        vertical = 4.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(34.dp)
                                )
                                AsyncImage(
                                    model = channel.logo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (favorite) "♥" else " ",
                                    color = Color(0xFFF87171),
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = channel.name,
                                    color = if (focused)
                                        Color.White
                                    else
                                        Color.White.copy(alpha = 0.88f),
                                    fontSize = 15.sp,
                                    fontWeight = if (focused)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (playing) {
                                    Text(
                                        text = "▶",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                    } else {

                        itemsIndexed(
                            activeCategories,
                            key = { _, category -> category }
                        ) { index, category ->

                            val focused =
                                index == fullscreenCategoryIndex

                            val count =
                                categoryChannelCounts[category] ?: 0

                            val selected =
                                index == selectedCategoryIndex

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            focused -> Color(0xFF164E63)
                                            selected -> Color(0x6638BDF8)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (focused) 1.5.dp else 0.dp,
                                        color = if (focused)
                                            Color(0xFF38BDF8)
                                        else
                                            Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 11.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "▣",
                                    color = if (focused)
                                        Color(0xFF38BDF8)
                                    else
                                        Color.White.copy(alpha = 0.6f),
                                    fontSize = 16.sp,
                                    modifier = Modifier.width(30.dp)
                                )

                                Text(
                                    text = category,
                                    color = if (focused)
                                        Color.White
                                    else
                                        Color.White.copy(alpha = 0.88f),
                                    fontSize = 15.sp,
                                    fontWeight = if (focused)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "($count)",
                                    color = if (focused)
                                        Color(0xFF38BDF8)
                                    else
                                        Color.White.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
