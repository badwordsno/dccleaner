package com.dccleaner.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI

object GuestbookUserListFetcher {
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") {
            "http 또는 https 링크만 사용할 수 있습니다."
        }

        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "text/plain,text/html,*/*")
        connection.setRequestProperty("User-Agent", "DCCleaner")

        try {
            if (connection.responseCode !in 200..299) {
                error("유저 리스트를 불러오지 못했습니다. (${connection.responseCode})")
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            extractUserListText(response).takeIf { it.isNotBlank() }
                ?: error("링크에서 유저 ID를 찾지 못했습니다.")
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractUserListText(response: String): String {
        val preText = Jsoup.parse(response).selectFirst("pre")?.wholeText()
        val source = preText ?: response
        return source.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}
