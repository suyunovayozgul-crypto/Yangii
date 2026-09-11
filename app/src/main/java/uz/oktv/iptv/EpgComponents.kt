package uz.oktv.iptv

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun EpgScheduleDialog(
    context: Context,
    channelSchedule: List<EpgProgram>,
    currentLang: AppLang,
    currentChannelName: String,
    currentChannelUrl: String?,
    epgSelectedIndex: Int,
    epgListState: LazyListState,
    onEpgSelectedIndexChange: (Int) -> Unit,
    onActiveStreamUrlChange: (String?) -> Unit,
    onPlayingArchiveProgramChange: (EpgProgram?) -> Unit,
    onDismiss: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onShowInfoBar: () -> Unit
) {
    val totalDays = channelSchedule
        .map { it.dateStr }
        .distinct()
        .filter { it.isNotEmpty() }
        .size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .height(460.dp)
                .background(
                    Color(0xFF0D1322),
                    RoundedCornerShape(12.dp)
                )
                .denimDoubleBorder(12f, 10f)
                .padding(16.dp)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${Strings.get("epg_schedule", currentLang)}: $currentChannelName",
                        color = Color(0xFFF59E0B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (totalDays > 0) {
                        Text(
                            text = "↺ ($totalDays дн.)",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "✕",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(start = 8.dp)
                )
            }

            if (channelSchedule.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Телепрограмма не загружена. Нажмите «Обновить EPG» в боковом меню.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = epgListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        channelSchedule,
                        key = { _, prog ->
                            "${prog.startTimeMs}-${prog.title}"
                        }
                    ) { index, epgItem ->

                        val now = System.currentTimeMillis()

                        val isLive =
                            epgItem.startTimeMs <= now &&
                            (epgItem.stopTimeMs == 0L ||
                                epgItem.stopTimeMs > now)

                        val isArchive =
                            epgItem.stopTimeMs > 0L &&
                            epgItem.stopTimeMs <= now

                        val isEpgFocused =
                            index == epgSelectedIndex

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isEpgFocused) {
                                        Color(0xFF1E293B)
                                    } else if (isLive) {
                                        Color(0xFF1E2F48)
                                    } else {
                                        Color(0xFF161822)
                                    }
                                )
                                .border(
                                    width = if (isLive || isEpgFocused) {
                                        1.5.dp
                                    } else {
                                        0.dp
                                    },
                                    color = if (isEpgFocused) {
                                        Color(0xFF2563EB)
                                    } else if (isLive) {
                                        Color(0xFFFFD700).copy(alpha = 0.5f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = isArchive || isLive) {

                                    onEpgSelectedIndexChange(index)

                                    if (isArchive) {
                                        val startUnix =
                                            epgItem.startTimeMs / 1000

                                        val endUnix =
                                            (epgItem.stopTimeMs / 1000) + 60

                                        if (currentChannelUrl != null) {
                                            val sep =
                                                if (currentChannelUrl.contains("?")) {
                                                    "&"
                                                } else {
                                                    "?"
                                                }

                                            onActiveStreamUrlChange(
                                                "${currentChannelUrl}${sep}utc=$startUnix&lutc=$endUnix"
                                            )

                                            onPlayingArchiveProgramChange(epgItem)

                                            Toast.makeText(
                                                context,
                                                "Архив: ${epgItem.title}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else if (isLive) {
                                        onActiveStreamUrlChange(
                                            currentChannelUrl
                                        )

                                        onPlayingArchiveProgramChange(null)

                                        Toast.makeText(
                                            context,
                                            "Прямой эфир: ${epgItem.title}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    onDismiss()
                                    onEnterFullscreen()
                                    onShowInfoBar()
                                }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (epgItem.dateStr.isNotEmpty()) {
                                    Text(
                                        text = epgItem.dateStr,
                                        color = Color(0xFF808A9D),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(42.dp)
                                    )
                                }

                                Text(
                                    text = "${epgItem.startTimeStr} - ${epgItem.stopTimeStr}",
                                    color = when {
                                        isLive && !isEpgFocused ->
                                            Color(0xFFFFD700)

                                        isEpgFocused ->
                                            Color.White

                                        else ->
                                            Color(0xFF38BDF8)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(88.dp)
                                )

                                Text(
                                    text = epgItem.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isLive) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                when {
                                    isLive -> {
                                        Text(
                                            text = "🔴 СЕЙЧАС",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier
                                                .background(
                                                    Color(0xFFFFD700),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                )
                                        )
                                    }

                                    isArchive -> {
                                        Text(
                                            text = "↺ АРХИВ",
                                            color = if (isEpgFocused) {
                                                Color.White
                                            } else {
                                                Color(0xFF10B981)
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(
                                                    if (isEpgFocused) {
                                                        Color(0x33FFFFFF)
                                                    } else {
                                                        Color(0xFF1E293B)
                                                    },
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                )
                                        )
                                    }

                                    else -> {
                                        Text(
                                            text = "⏰",
                                            color = Color(0xFFA0A5B5),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (epgItem.desc.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = epgItem.desc,
                                    color = if (isEpgFocused) {
                                        Color.White
                                    } else {
                                        Color(0xFFA0A5B5)
                                    },
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
