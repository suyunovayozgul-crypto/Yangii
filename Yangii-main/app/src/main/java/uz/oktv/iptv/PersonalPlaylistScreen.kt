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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Playlist tanlash ekrani.
 *
 * MUHIM: bu yerda foydalanuvchi hech qanday havola (URL) qo'lda kirita
 * olmaydi va mavjud playlistlarning haqiqiy manzilini ko'rmaydi — ular
 * ilova ichiga qattiq o'rnatilgan (PersonalPlaylistStore.BUILTIN_PLAYLISTS).
 * Foydalanuvchiga faqat ular orasidan birini TANLASH imkoni beriladi.
 */
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
    var loading by remember { mutableStateOf(false) }
    var loadingId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun openPlaylist(playlist: PersonalPlaylist) {
        loading = true
        loadingId = playlist.id
        error = ""

        scope.launch(Dispatchers.IO) {
            try {
                val result = PersonalPlaylistLoader.load(playlist.url)

                if (result.channels.isEmpty()) {
                    throw IllegalStateException(
                        "Kanallar topilmadi"
                    )
                }

                launch(Dispatchers.Main) {
                    onPlaylistLoaded(playlist, result)
                    loading = false
                    loadingId = null
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    error = e.message ?: "Yuklashda xatolik"
                    loading = false
                    loadingId = null
                }
            }
        }
    }

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
                text = "MIROVOY TV",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Kanal ro'yxatini tanlang",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    playlists,
                    key = { it.id }
                ) { playlist ->
                    val isLoadingThis = loading && loadingId == playlist.id

                    Row(
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
                            .clickable(enabled = !loading) {
                                openPlaylist(playlist)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        if (isLoadingThis) {
                            CircularProgressIndicator(
                                color = Color(0xFF2563EB),
                                modifier = Modifier.width(18.dp).height(18.dp)
                            )
                        } else {
                            Text(
                                text = "TANLASH",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
