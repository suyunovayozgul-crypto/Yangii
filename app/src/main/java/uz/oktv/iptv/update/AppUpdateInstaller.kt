package uz.oktv.iptv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to Android's own system installer. This never
 * installs anything itself - it only starts the same "do you want to
 * install/update this app?" screen the user would see installing any APK
 * by hand, and Android requires the user to tap through it every time.
 * That confirmation cannot be skipped from app code; that's an OS security
 * boundary, not something this class chooses.
 */
object AppUpdateInstaller {

    /**
     * True if the app is currently allowed to *request* an install
     * (the OS-level "install unknown apps" toggle for this app). If false,
     * call [openUnknownAppsSettings] first - there is no way to install
     * without the user turning this on themselves.
     */
    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // Pre-O, the "unknown sources" toggle is a system-wide setting.
        }

    /** Sends the user to the system screen where they grant the install-permission toggle. */
    fun openUnknownAppsSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Launches the standard system install/update dialog for [apkFile].
     * Call [canRequestInstall] first; if it's false, this will still be
     * refused by the OS.
     */
    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
