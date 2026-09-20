package uz.oktv.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
    onPlaylistLoaded: (PersonalPlaylist, PersonalPlaylistLoadResult) -> Unit
) {
    var loading      by remember { mutableStateOf(false) }
    var loadingId    by remember { mutableStateOf<Long?>(null) }
    var error        by remember { mutableStateOf("") }

    // Yangi playlist qo'shish formi
    var showAddForm  by remember { mutableStateOf(false) }
    var newName      by remember { mutableStateOf("") }
    var newUrl       by remember { mutableStateOf("") }
    var addError     by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun openPlaylist(playlist: PersonalPlaylist) {
        loading   = true
        loadingId = playlist.id
        error     = ""

        scope.launch(Dispatchers.IO) {
            try {
                val result = PersonalPlaylistLoader.load(playlist.url)
                if (result.channels.isEmpty()) throw IllegalStateException("Kanallar topilmadi")

                launch(Dispatchers.Main) {
                    onPlaylistLoaded(playlist, result)
                    loading   = false
                    loadingId = null
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    error     = e.message ?: "Yuklashda xatolik"
                    loading   = false
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
                text       = "MIROVOY TV",
                color      = Color.White,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text     = "Kanal ro'yxatini tanlang",
                color    = Color(0xFF94A3B8),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(20.dp))

            if (error.isNotEmpty()) {
                Text(text = error, color = Color(0xFFEF4444), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier          = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ── Barcha playlistlar ──────────────────────────────────
                items(playlists, key = { it.id }) { playlist ->
                    val isLoadingThis = loading && loadingId == playlist.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1322), RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (store.getActiveId() == playlist.id) Color(0xFF2563EB)
                                else Color(0xFF1E293B),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !loading) { openPlaylist(playlist) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = playlist.name,
                                color      = Color.White,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Qattiq yozilganlarda URL ko'rinmaydi
                            if (!playlist.isBuiltin) {
                                Text(
                                    text     = playlist.url,
                                    color    = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        if (isLoadingThis) {
                            CircularProgressIndicator(
                                color    = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            // O'chirish tugmasi — faqat foydalanuvchi playlistlari
                            if (!playlist.isBuiltin) {
                                Text(
                                    text     = "✕",
                                    color    = Color(0xFFEF4444),
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .clickable(enabled = !loading) {
                                            store.delete(playlist.id)
                                            onPlaylistsChanged(store.load())
                                        }
                                        .padding(end = 12.dp)
                                )
                            }

                            Text(
                                text       = if (store.getActiveId() == playlist.id) "✓ FAOL" else "TANLASH",
                                color      = if (store.getActiveId() == playlist.id) Color(0xFF22C55E) else Color(0xFF38BDF8),
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Qo'shish tugmasi (limit bo'lmasa) ──────────────────
                if (store.canAddMore() && !showAddForm) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1322), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .clickable(enabled = !loading) { showAddForm = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text       = "+ O'z playlistimni qo'shish",
                                color      = Color(0xFF38BDF8),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Qo'shish formi ─────────────────────────────────────
                if (showAddForm) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1322), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text       = "Yangi playlist",
                                color      = Color.White,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Nom
                            BasicTextField(
                                value         = newName,
                                onValueChange = { newName = it },
                                textStyle     = TextStyle(color = Color.White, fontSize = 13.sp),
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070B14), RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                decorationBox = { inner ->
                                    if (newName.isEmpty()) Text("Nom (masalan: Mening TV)", color = Color(0xFF475569), fontSize = 13.sp)
                                    inner()
                                }
                            )

                            // URL
                            BasicTextField(
                                value         = newUrl,
                                onValueChange = { newUrl = it },
                                textStyle     = TextStyle(color = Color.White, fontSize = 13.sp),
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070B14), RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                decorationBox = { inner ->
                                    if (newUrl.isEmpty()) Text("URL (https://...)", color = Color(0xFF475569), fontSize = 13.sp)
                                    inner()
                                }
                            )

                            if (addError.isNotEmpty()) {
                                Text(text = addError, color = Color(0xFFEF4444), fontSize = 11.sp)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Saqlash
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF2563EB), RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (newUrl.trim().isEmpty()) {
                                                addError = "URL kiritilmadi"
                                            } else {
                                                val added = store.add(newName, newUrl)
                                                if (added != null) {
                                                    onPlaylistsChanged(store.load())
                                                    showAddForm = false
                                                    newName = ""
                                                    newUrl  = ""
                                                    addError = ""
                                                } else {
                                                    addError = "Maksimal 2 ta qo'shish mumkin"
                                                }
                                            }
                                        }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Saqlash", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                // Bekor qilish
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                        .clickable {
                                            showAddForm = false
                                            newName = ""
                                            newUrl  = ""
                                            addError = ""
                                        }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Bekor", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
