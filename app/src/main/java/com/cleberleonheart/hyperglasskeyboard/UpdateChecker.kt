package com.cleberleonheart.hyperglasskeyboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val version: String,
    val url: String,
    val notes: String,
    val apkUrl: String = ""
)

sealed class UpdateState {
    data class Available(val info: UpdateInfo) : UpdateState()
    data object UpToDate : UpdateState()
    data object Error : UpdateState()
}

class UpdateCheckHandle internal constructor(private val cancelled: AtomicBoolean) {
    fun cancel() {
        cancelled.set(true)
    }
}

sealed class UpdateDownloadState {
    data class Progress(val percent: Int) : UpdateDownloadState()
    data object Ready : UpdateDownloadState()
    data object Failed : UpdateDownloadState()
}

class UpdateDownloadHandle internal constructor(private val cancelled: AtomicBoolean) {
    fun cancel() {
        cancelled.set(true)
    }
}

object UpdateChecker {
    private const val UPDATE_FILE = "update.apk"
    private const val RELEASES_URL = "https://api.github.com/repos/cleberleonheart-maker/keyboard-/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/cleberleonheart-maker/keyboard-/releases"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hyperglass-update").apply { isDaemon = true }
    }

    fun check(callback: (UpdateState) -> Unit): UpdateCheckHandle {
        val cancelled = AtomicBoolean(false)
        val handle = UpdateCheckHandle(cancelled)
        executor.execute {
            if (cancelled.get()) return@execute
            val state = fetchState(BuildConfig.VERSION_NAME)
            if (!cancelled.get()) {
                mainHandler.post {
                    if (!cancelled.get()) callback(state)
                }
            }
        }
        return handle
    }

    private fun fetchState(appVersion: String): UpdateState = try {
        val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doInput = true
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "HyperGlassKeyboard")
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                    val version = tag.trim().removePrefix("v").removePrefix("V")
                    val apkUrl = json.optJSONArray("assets")?.let { assets ->
                        (0 until assets.length()).firstNotNullOfOrNull { i ->
                            val asset = assets.optJSONObject(i) ?: return@firstNotNullOfOrNull null
                            val url = asset.optString("browser_download_url")
                            url.takeIf {
                                it.startsWith("https://github.com/") && it.endsWith(".apk", ignoreCase = true)
                            }
                        }
                    }.orEmpty()
                    if (version.isNotBlank() && VersionComparator.isNewer(version, appVersion)) {
                        UpdateState.Available(
                            UpdateInfo(
                                version = version,
                                url = safeReleaseUrl(json.optString("html_url")),
                                notes = json.optString("body"),
                                apkUrl = apkUrl
                            )
                        )
                    } else {
                        UpdateState.UpToDate
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateState.UpToDate
                else -> UpdateState.Error
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        UpdateState.Error
    }

    fun download(
        context: Context,
        info: UpdateInfo,
        callback: (UpdateDownloadState) -> Unit
    ): UpdateDownloadHandle {
        val cancelled = AtomicBoolean(false)
        val appContext = context.applicationContext
        executor.execute {
            val file = File(appContext.cacheDir, UPDATE_FILE)
            try {
                if (info.apkUrl.isBlank()) throw IllegalArgumentException("missing apk url")
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "HyperGlassKeyboard")
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("download failed")
                    val total = conn.contentLength
                    conn.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var done = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (cancelled.get()) return@execute
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                                    if (lastPercent < 0 || percent / 10 != lastPercent / 10) {
                                        lastPercent = percent
                                        mainHandler.post { callback(UpdateDownloadState.Progress(percent)) }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (!cancelled.get()) mainHandler.post { callback(UpdateDownloadState.Ready) }
            } catch (e: Exception) {
                file.delete()
                if (!cancelled.get()) mainHandler.post { callback(UpdateDownloadState.Failed) }
            }
        }
        return UpdateDownloadHandle(cancelled)
    }

    fun install(context: Context): Boolean {
        val file = File(context.cacheDir, UPDATE_FILE)
        if (!file.exists() || !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                false
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
        val uri = Uri.parse("content://${context.packageName}.apkprovider/$UPDATE_FILE")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun open(context: Context, info: UpdateInfo) {
        val uri = Uri.parse(safeReleaseUrl(info.url))
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    private fun safeReleaseUrl(url: String): String = url.takeIf {
        it.startsWith("https://github.com/") || it.startsWith("http://github.com/")
    } ?: RELEASES_PAGE

}

internal object VersionComparator {
    fun isNewer(remote: String, local: String): Boolean {
        val r = numbers(remote)
        val l = numbers(local)
        if (r.isEmpty()) return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun numbers(v: String): List<Int> =
        Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()
}
