package io.github.rubayet123.tvlive.data.network

import android.util.Log
import io.github.rubayet123.tvlive.model.StreamSource
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

object StreamProbeManager {
    private const val TAG = "StreamProbeManager"
    private const val PROBE_TIMEOUT_MS = 2200L

    /**
     * Probes candidate sources in parallel and returns the first reachable source (HTTP 2xx/3xx response).
     * If all fail or time out, returns null.
     */
    suspend fun findFirstWorkingSource(
        sources: List<StreamSource>,
        defaultUserAgent: String
    ): Pair<Int, StreamSource>? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext null
        if (sources.size == 1) return@withContext Pair(0, sources[0])

        Log.d(TAG, "Starting parallel probe for ${sources.size} sources...")

        val deferreds = sources.mapIndexed { index, source ->
            async {
                val isWorking = probeSingleSource(source, defaultUserAgent)
                if (isWorking) Pair(index, source) else null
            }
        }

        val winnerDeferred = CompletableDeferred<Pair<Int, StreamSource>?>()

        val watcher = launch {
            val completedSet = mutableSetOf<Int>()
            while (completedSet.size < deferreds.size && isActive) {
                deferreds.forEachIndexed { idx, d ->
                    if (idx !in completedSet && d.isCompleted) {
                        completedSet.add(idx)
                        val res = d.getCompleted()
                        if (res != null) {
                            Log.d(TAG, "Parallel probe winner found: Index ${res.first} (${res.second.providerName})")
                            winnerDeferred.complete(res)
                            return@launch
                        }
                    }
                }
                delay(40)
            }
            if (!winnerDeferred.isCompleted) {
                winnerDeferred.complete(null)
            }
        }

        val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            winnerDeferred.await()
        }

        watcher.cancel()
        deferreds.forEach { if (!it.isCompleted) it.cancel() }

        if (result == null) {
            Log.d(TAG, "Parallel probe completed with no fast winner within ${PROBE_TIMEOUT_MS}ms.")
        }

        result
    }

    private fun probeSingleSource(source: StreamSource, defaultUserAgent: String): Boolean {
        val urlStr = source.streamUrl
        if (urlStr.startsWith("damitv://") || urlStr.startsWith("roarzone://") || urlStr.startsWith("pyramid://") || urlStr.startsWith("toffee://")) {
            // Virtual scrapers, skip pre-flight probing
            return false
        }
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1800
            connection.readTimeout = 1800

            val customHeaders = source.headers ?: emptyMap()
            val userAgent = customHeaders["User-Agent"] ?: customHeaders["user-agent"] ?: defaultUserAgent
            connection.setRequestProperty("User-Agent", userAgent)

            customHeaders.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    connection.setRequestProperty(k, v)
                }
            }

            connection.setRequestProperty("Range", "bytes=0-512")
            connection.instanceFollowRedirects = true

            val code = connection.responseCode
            connection.disconnect()
            Log.d(TAG, "Probe URL $urlStr returned HTTP $code")
            code in 200..399
        } catch (e: Exception) {
            Log.d(TAG, "Probe URL $urlStr failed: ${e.message}")
            false
        }
    }
}
