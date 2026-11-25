package com.cleberleonheart.hyperglasskeyboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val version: String,
    val url: String,
    val notes: String
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

object UpdateChecker {
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
                    if (version.isNotBlank() && VersionComparator.isNewer(version, appVersion)) {
                        UpdateState.Available(
                            UpdateInfo(
                                version = version,
                                url = safeReleaseUrl(json.optString("html_url")),
                                notes = json.optString("body")
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
