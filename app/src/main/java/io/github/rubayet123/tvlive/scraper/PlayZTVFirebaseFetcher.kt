package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Firebase Remote Config fetcher for PlayZ TV.
 */
object PlayZTVFirebaseFetcher {

    private const val TAG = "PlayZFirebase"
    private const val PACKAGE_NAME = "com.playz.tv"
    private const val API_KEY = "AIzaSyDKRqLlbaZBIpHzLBiQTUrJqr3gN-nDWWc"
    private const val APP_ID = "1:516859456626:android:12a75869902c4f8a6826eb"
    private const val PROJECT_NUMBER = "516859456626"
    private const val APP_VERSION = "2.1"
    private const val APP_BUILD = "4"
    private const val SDK_VERSION = "22.1.0"

    private val client: OkHttpClient by lazy {
        NetworkClient.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getBaseApiUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://firebaseremoteconfig.googleapis.com/v1/projects/$PROJECT_NUMBER/namespaces/firebase:fetch"
            val appInstanceId = UUID.randomUUID().toString().replace("-", "")

            val payload = """
                {
                    "appInstanceId": "$appInstanceId",
                    "appInstanceIdToken": "",
                    "appId": "$APP_ID",
                    "countryCode": "US",
                    "languageCode": "en-US",
                    "platformVersion": "30",
                    "timeZone": "UTC",
                    "appVersion": "$APP_VERSION",
                    "appBuild": "$APP_BUILD",
                    "packageName": "$PACKAGE_NAME",
                    "sdkVersion": "$SDK_VERSION",
                    "analyticsUserProperties": {}
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Android-Package", PACKAGE_NAME)
                .header("X-Goog-Api-Key", API_KEY)
                .header("X-Google-GFE-Can-Retry", "yes")
                .header("User-Agent", "okhttp/4.12.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val entries = json.optJSONObject("entries")
                        val apiUrl = entries?.optString("api_url")
                        if (!apiUrl.isNullOrBlank()) {
                            return@withContext apiUrl.trimEnd('/')
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Firebase Remote Config fetch failed: ${e.message}")
            null
        }
    }
}
