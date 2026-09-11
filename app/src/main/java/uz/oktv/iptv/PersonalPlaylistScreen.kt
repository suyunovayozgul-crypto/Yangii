package uz.oktv.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PersonalPlaylistScreen(
    store: PersonalPlaylistStore,
    playlists: List<PersonalPlaylist>,
    onPlaylistsChanged: (List<PersonalPlaylist>) -> Unit,
    onPlaylistLoaded: (
        PersonalPlaylist,
        PersonalPlaylistLoadResult
    ) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "OKTV PERSONAL",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Добавьте свой IPTV-плейлист",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            PersonalInput(
                value = name,
                onValueChange = { name = it },
                hint = "Название плейлиста"
            )

            Spacer(Modifier.height(10.dp))

            PersonalInput(
                value = url,
                onValueChange = { url = it },
                hint = "https://...m3u или https://...m3u8"
            )

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF2563EB),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(
                        enabled = !loading && url.isNotBlank()
                    ) {
                        loading = true
                        error = ""

                        scope.launch(Dispatchers.IO) {
                            try {
                                val result =
                                    PersonalPlaylistLoader.load(url)

                                if (result.channels.isEmpty()) {
                                    throw IllegalStateException(
                                        "В плейлисте не найдено каналов"
                                    )
                                }

                                val item =
                                    store.add(
                                        name = name.ifBlank {
                                            "Плейлист ${playlists.size + 1}"
                                        },
                                        url = url
                                    )

                                val updated = store.load()

                                launch(Dispatchers.Main) {
                                    onPlaylistsChanged(updated)
                                    onPlaylistLoaded(item, result)
                                    loading = false
                                }

                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    error =
                                        e.message ?: "Ошибка загрузки плейлиста"
                                    loading = false
                                }
                            }
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (loading) {
                        "ЗАГРУЗКА..."
                    } else {
                        "ДОБАВИТЬ И ОТКРЫТЬ"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))

                Text(
                    text = error,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp
                )
            }

            if (playlists.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "СОХРАНЁННЫЕ ПЛЕЙЛИСТЫ",
                    color = Color(0xFFF59E0B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        playlists,
                        key = { it.id }
                    ) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFF0D1322),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    playlist.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    playlist.url,
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                "ОТКРЫТЬ",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    loading = true
                                    error = ""

                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val result =
                                                PersonalPlaylistLoader.load(
                                                    playlist.url
                                                )

                                            launch(Dispatchers.Main) {
                                                onPlaylistLoaded(
                                                    playlist,
                                                    result
                                                )
                                                loading = false
                                            }
                                        } catch (e: Exception) {
                                            launch(Dispatchers.Main) {
                                                error =
                                                    e.message
                                                        ?: "Ошибка загрузки"
                                                loading = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF0D1322),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                Color(0xFF1E293B),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = hint,
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 12.sp
            )
        )
    }
}
