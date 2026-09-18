package uz.oktv.iptv.update

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed class UpdateState {
    object Idle : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    object NeedsPermission : UpdateState()
}

/**
 * Drop this once near the top of your main screen's composable, e.g. right
 * next to your other top-level dialogs in MainActivity:
 *
 *     UpdateCheckerHost()
 *
 * It checks GitHub Releases once when it enters composition (app launch)
 * and, if a newer build exists, walks the user through: "update available"
 * -> download with a progress bar -> the standard system install screen.
 * Every step is a plain dialog the user can dismiss; nothing downloads or
 * installs silently, and the final install step is always the normal
 * Android system confirmation - that part can't be skipped from app code.
 */
@Composable
fun UpdateCheckerHost() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val currentVersionCode = try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.longVersionCode.toInt()
            else
                info.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            return@LaunchedEffect
        }

        val update = withContext(Dispatchers.IO) {
            UpdateChecker.checkForUpdate(currentVersionCode)
        }
        if (update != null) {
            state = UpdateState.Available(update)
        }
    }

    when (val s = state) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = { state = UpdateState.Idle },
                title = { Text("Yangi versiya mavjud: ${s.info.versionName}") },
                text = {
                    Text(s.info.releaseNotes.ifBlank { "Yangilanishlar mavjud." })
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (!AppUpdateInstaller.canRequestInstall(context)) {
                            state = UpdateState.NeedsPermission
                        } else {
                            state = UpdateState.Downloading(0)
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ApkDownloader.download(context, s.info.downloadUrl) { progress ->
                                        state = UpdateState.Downloading(progress)
                                    }
                                }
                                state = if (file != null) {
                                    UpdateState.ReadyToInstall(file)
                                } else {
                                    UpdateState.Idle
                                }
                            }
                        }
                    }) { Text("Yuklab olish") }
                },
                dismissButton = {
                    TextButton(onClick = { state = UpdateState.Idle }) { Text("Keyinroq") }
                }
            )
        }

        is UpdateState.NeedsPermission -> {
            AlertDialog(
                onDismissRequest = { state = UpdateState.Idle },
                title = { Text("Ruxsat kerak") },
                text = {
                    Text(
                        "Ilovani yangilash uchun \"Noma'lum ilovalarni o'rnatish\" " +
                            "ruxsatini shu ilova uchun yoqib qo'ying, so'ng qayta urinib ko'ring."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        AppUpdateInstaller.openUnknownAppsSettings(context)
                        state = UpdateState.Idle
                    }) { Text("Sozlamalarni ochish") }
                },
                dismissButton = {
                    TextButton(onClick = { state = UpdateState.Idle }) { Text("Bekor qilish") }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = { /* not dismissible mid-download */ },
                title = { Text("Yuklanmoqda...") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = { s.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${s.progress}%", modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateState.ReadyToInstall -> {
            LaunchedEffect(s.file) {
                AppUpdateInstaller.install(context, s.file)
                state = UpdateState.Idle
            }
        }

        UpdateState.Idle -> Unit
    }
}
