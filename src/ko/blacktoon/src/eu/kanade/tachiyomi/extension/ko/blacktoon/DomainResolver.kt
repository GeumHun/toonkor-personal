package eu.kanade.tachiyomi.extension.ko.blacktoon

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class DomainResolver(
    private val preferences: SharedPreferences,
    client: OkHttpClient,
) {
    private val probeClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private val lock = Any()

    val currentBaseUrl: String
        get() = "https://blacktoon${preferences.getInt(DOMAIN_KEY, DEFAULT_DOMAIN)}.com"

    fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!DOMAIN_REGEX.matches(original.url.host)) return chain.proceed(original)

        val first = rewrite(original, currentBaseUrl)
        try {
            val response = chain.proceed(first)
            if (response.isSuccessful && DOMAIN_REGEX.matches(response.request.url.host)) return response
            val replacement = findWorkingDomain() ?: return response
            response.close()
            return chain.proceed(rewrite(original, replacement))
        } catch (error: IOException) {
            val replacement = findWorkingDomain() ?: throw error
            return chain.proceed(rewrite(original, replacement))
        }
    }

    private fun findWorkingDomain(): String? = synchronized(lock) {
        val current = preferences.getInt(DOMAIN_KEY, DEFAULT_DOMAIN)
        if (probe(current)) return@synchronized domain(current)
        for (number in (current + 1)..(current + MAX_SCAN)) {
            if (probe(number)) {
                preferences.edit().putInt(DOMAIN_KEY, number).commit()
                return@synchronized domain(number)
            }
        }
        null
    }

    private fun probe(number: Int): Boolean = runCatching {
        probeClient.newCall(GET(domain(number))).execute().use { response ->
            response.isSuccessful &&
                response.request.url.host == "blacktoon$number.com" &&
                response.peekBody(MAX_PROBE_BODY).string().contains("data/webtoon")
        }
    }.getOrDefault(false)

    private fun rewrite(request: okhttp3.Request, target: String): okhttp3.Request {
        val host = target.toHttpUrl().host
        return request.newBuilder()
            .url(request.url.newBuilder().host(host).build())
            .header("Referer", "$target/")
            .header("Origin", target)
            .build()
    }

    private fun domain(number: Int) = "https://blacktoon$number.com"

    private companion object {
        const val DOMAIN_KEY = "blacktoon_domain_number"
        const val DEFAULT_DOMAIN = 423
        const val MAX_SCAN = 10
        const val MAX_PROBE_BODY = 512L * 1024L
        val DOMAIN_REGEX = Regex("blacktoon\\d+\\.com")
    }
}
