package app.localcache

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.localcache.model.StreamItem
import app.localcache.storage.CacheRegistry
import app.localcache.storage.DownloadEngine
import app.localcache.storage.LocalLibrary
import app.localcache.storage.StorageMode
import app.localcache.storage.UsbDriveDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** External-player destination. No playback and no transfer before explicit confirmation. */
class ExternalDownloadActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var content: LinearLayout
    private lateinit var message: TextView
    private lateinit var rows: LinearLayout
    private var candidate: ExternalLink.Candidate? = null
    private var confirm: Button? = null
    private var receipt: TextView? = null
    private var renderedKeys = emptyList<String>()
    private val rowViews = mutableMapOf<String, Row>()
    private data class Row(val status: TextView, val bar: ProgressBar, val resume: Button, val pause: Button)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(20), dp(32), dp(20))
        }
        setContentView(ScrollView(this).apply { addView(content) })
        content.addView(text("Descargar al USB", 26f))
        message = text("Comprobando descargas…", 17f)
        content.addView(message)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // The receipt must appear before the persistent progress list.
        accept(intent)
        content.addView(rows)
        content.addView(button("Configurar USB / abrir Local Cache") {
            startActivity(Intent(this, MainActivity::class.java))
        })
        content.addView(button("Volver (la descarga continúa)") { finish() })
        scope.launch {
            withContext(Dispatchers.IO) { LocalLibrary.rehydrate(this@ExternalDownloadActivity) }
            while (true) {
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) refresh()
                delay(1_000)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(input: Intent) {
        confirm?.let { content.removeView(it) }
        receipt?.let { content.removeView(it) }
        confirm = null
        receipt = null
        candidate = null
        if (input.action != Intent.ACTION_VIEW) return
        val title = runCatching { input.getStringExtra("title") ?: input.getStringExtra(Intent.EXTRA_TITLE) }.getOrNull()
        candidate = ExternalLink.parse(input.dataString, title)
        val value = candidate
        if (value == null) {
            receipt = text("Enlace no admitido. Selecciona una fuente HTTP(S) de Real-Debrid, no un magnet.", 18f)
            content.addView(receipt, 2)
            return
        }
        val mime = input.type?.take(80).orEmpty()
        // Diagnostics contain no URL, title, token or arbitrary extra values.
        val keys = runCatching { input.extras?.keySet().orEmpty().filter { it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) }.sorted().joinToString(",") }.getOrDefault("")
        getSharedPreferences(BuildConfig.PREFS_FILE, MODE_PRIVATE).edit()
            .putLong("external_received_at", System.currentTimeMillis())
            .putString("external_received_scheme", java.net.URI(value.url).scheme)
            .putString("external_received_mime", mime)
            .putString("external_received_extra_keys", keys).apply()
        receipt = text("Enlace recibido · ${java.net.URI(value.url).scheme?.uppercase()}\n${value.title}\nNo se reproduce ni se descarga hasta confirmar.", 19f)
        content.addView(receipt, 2)
        confirm = button("Descargar al USB") { enqueue(value) }.also {
            content.addView(it, 3)
            it.requestFocus()
        }
    }

    private fun enqueue(value: ExternalLink.Candidate) {
        scope.launch {
            confirm?.isEnabled = false
            try {
                val storageError = withContext(Dispatchers.IO) {
                    val root = Prefs.cacheDirPath(this@ExternalDownloadActivity)?.let { File(it) }
                    when {
                        StorageMode.isInternal(this@ExternalDownloadActivity) || root == null -> "Selecciona primero el USB en Local Cache."
                        !root.isDirectory -> "El USB no está conectado. Vuelve a seleccionarlo en Local Cache."
                        !UsbDriveDetector.checkCacheDir(root).writable -> "No se puede escribir en el USB."
                        else -> null
                    }
                }
                if (storageError != null) { message.text = storageError; return@launch }
                // A hashed identity, never a path or id supplied by an external app.
                CacheRegistry.register(StreamItem(
                    cacheKey = value.key, source = "Externo", label = value.title,
                    rawName = value.title, url = value.url, title = value.title,
                ), type = "external", id = value.key)
                CacheForegroundService.start(this@ExternalDownloadActivity)
                DownloadEngine.queueOrStart(this@ExternalDownloadActivity, value.key)
                confirm?.visibility = View.GONE
                receipt?.text = value.title
                refresh()
            } catch (_: Exception) {
                message.text = "No se pudo iniciar la descarga. Comprueba el USB y el servicio."
            } finally { confirm?.isEnabled = true }
        }
    }

    private fun refresh() {
        val entries = CacheRegistry.all().filter {
            it.downloadedBytes > 0 || it.status in setOf("downloading", "queued", "paused", "error", "complete")
        }.sortedBy { it.cacheKey }
        val keys = entries.map { it.cacheKey }
        if (keys != renderedKeys) {
            rows.removeAllViews()
            rowViews.clear()
            renderedKeys = keys
            entries.forEach { e ->
                rows.addView(text(e.label, 19f))
                val status = text("", 17f).also { rows.addView(it) }
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
                rows.addView(bar, LinearLayout.LayoutParams(-1, dp(12)))
                val controls = LinearLayout(this)
                val resume = button("Iniciar / reanudar") {
                    scope.launch {
                        try {
                            if (!StorageMode.isReady(this@ExternalDownloadActivity)) return@launch
                            CacheForegroundService.start(this@ExternalDownloadActivity)
                            DownloadEngine.queueOrStart(this@ExternalDownloadActivity, e.cacheKey)
                        } catch (_: Exception) { message.text = "No se pudo reanudar. Comprueba el USB." }
                    }
                }
                val pause = button("Pausar (conservar parcial)") {
                    if (DownloadEngine.activeKey() == e.cacheKey) DownloadEngine.cancelActive(deletePartial = false)
                }
                controls.addView(resume)
                controls.addView(pause)
                rows.addView(controls)
                rowViews[e.cacheKey] = Row(status, bar, resume, pause)
            }
        }
        val root = Prefs.cacheDirPath(this)
        val summary = if (root == null || StorageMode.isInternal(this)) "Configura el USB antes de descargar."
        else "Destino: USB · actualización cada segundo\n${entries.size} descarga(s). Puedes salir: continúan en segundo plano."
        if (message.text.toString() != summary) message.text = summary
        entries.forEach { e ->
            val row = rowViews[e.cacheKey] ?: return@forEach
            val percent = CacheRegistry.progress(e.cacheKey)
            val state = when (e.status) {
                "complete" -> "Completada"
                "downloading" -> if (percent >= 99) "Guardando y verificando…" else "Descargando"
                "queued" -> "En cola"
                "paused", "cancelled" -> "Pausada"
                "error" -> "Error (parcial conservado)"
                else -> if (e.downloadedBytes > 0) "Incompleta (puedes reanudar)" else "Pendiente"
            }
            val total = if (e.totalBytes > 0) size(e.totalBytes) else "tamaño pendiente"
            val speed = if (e.status == "downloading") " · ${size(e.bytesPerSec)}/s" else ""
            val details = "$state · $percent%\n${size(e.downloadedBytes)} / $total$speed" +
                (e.lastError?.let { "\n${SecretRedactor.redact(it)}" } ?: "")
            if (row.status.text.toString() != details) row.status.text = details
            row.bar.isIndeterminate = e.totalBytes <= 0 && e.status == "downloading"
            row.bar.progress = percent
            row.resume.isEnabled = e.status !in setOf("complete", "downloading", "queued")
            row.pause.isEnabled = DownloadEngine.activeKey() == e.cacheKey
        }
    }

    private fun text(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
        else -> String.format(Locale.getDefault(), "%.1f MiB", bytes / (1024.0 * 1024))
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
