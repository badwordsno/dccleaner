package com.dccleaner.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

object ReleaseVersionChecker {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/dccleaner3/dccleaner/releases/latest"

    suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        val connection = URI(LATEST_RELEASE_URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("User-Agent", "DCCleaner")

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(response)
                .jsonObject["tag_name"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.removePrefix("v")
                ?.takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }
}
