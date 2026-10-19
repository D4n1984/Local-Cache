package app.localcache

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.localcache.storage.DownloadEngine
import app.localcache.storage.StorageMode
import app.localcache.storage.UsbDriveDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Download-manager home. No add-on, manifest credentials, server or cache wipe controls. */
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var info: TextView
    private var selectionBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeUnusedAddonConfig()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (32 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        layout.addView(TextView(this).apply { text = "Descargas USB"; textSize = 28f })
        info = TextView(this).apply { textSize = 18f; setPadding(0, 24, 0, 24) }
        layout.addView(info)
        layout.addView(button("Ver descargas / progreso") {
            startActivity(Intent(this, ExternalDownloadActivity::class.java))
        }.apply { requestFocus() })
        layout.addView(button("Elegir USB → Movies") { chooseUsb() })
        layout.addView(button("Autorizar acceso a archivos") { requestFilesAccess() })
        layout.addView(TextView(this).apply {
            text = "Selecciona Torrentio RD en Stremio y envía el enlace al reproductor externo Descargar al USB.\n\n" +
                "Movies está en la raíz del USB, fuera de Android/data. Los archivos sobreviven a la desinstalación.\n" +
                "No hay servidor que arrancar. No se borran películas automáticamente al llenarse el USB."
            textSize = 17f
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        if (selectionBusy) return
        val path = Prefs.cacheDirPath(this)
        val permission = hasFilesAccess()
        info.text = "Destino: ${path ?: "sin seleccionar"}\n" +
            "Acceso a archivos: ${if (permission) "autorizado" else "pendiente"}\n" +
            if (path != null && !path.endsWith("/Movies")) "Pulsa Elegir USB para trasladar la biblioteca anterior a Movies." else "Carpeta persistente: Movies."
    }

    private fun hasFilesAccess() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    private fun requestFilesAccess() {
        if (hasFilesAccess()) { refresh(); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            info.text = "Este gestor está preparado para Android 11 o posterior."
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            info.text = "La TV no ofrece esta pantalla. Autoriza el acceso especial a archivos desde Ajustes o ADB."
        }
    }

    private fun chooseUsb() {
        if (selectionBusy) return
        if (DownloadEngine.isBusy()) { info.text = "Pausa la descarga antes de cambiar de carpeta."; return }
        if (!hasFilesAccess()) { requestFilesAccess(); return }
        scope.launch {
            val drives = withContext(Dispatchers.IO) { UsbDriveDetector.scan(this@MainActivity) }
            if (drives.isEmpty()) { info.text = "No se ha encontrado un USB conectado."; return@launch }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("USB → Movies (conserva los archivos)")
                .setItems(drives.map { "${it.label} · ${it.path}/Movies" }.toTypedArray()) { _, index ->
                    selectionBusy = true
                    info.text = "Comprobando Movies y trasladando archivos propios…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                StorageMode.selectUsb(this@MainActivity, drives[index].path)
                            }
                            info.text = result.message
                        } catch (_: Exception) { info.text = "No se pudo seleccionar Movies. Los archivos se conservan." }
                        finally { selectionBusy = false }
                    }
                }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun removeUnusedAddonConfig() {
        // Delete only duplicated addon credentials; never clear resume_secrets or storage selection.
        Prefs.setTorrentioManifestUrls(this, emptyList())
        Prefs.setCometManifestUrls(this, emptyList())
        Prefs.setLocalCometManifestUrls(this, emptyList())
        Prefs.setAutoNextEpisode(this, false)
        Prefs.setAllowTorrents(this, false)
        Prefs.setDebugLog(this, false)
        getSharedPreferences(BuildConfig.PREFS_FILE, MODE_PRIVATE).edit().remove("admin_token").apply()
        File(filesDir, "local-cache-config.json").delete()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
