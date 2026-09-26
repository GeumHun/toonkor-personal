package eu.kanade.tachiyomi.extension.ko.blacktoon

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

@Source
abstract class Blacktoon : HttpSource() {

    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    private val domainResolver by lazy { DomainResolver(preferences, network.client) }

    override val client = network.client.newBuilder()
        .addInterceptor { domainResolver.intercept(it) }
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheLock = Any()
    private var cacheHost = ""
    private var seriesCache: Map<Int, List<SeriesItem>> = emptyMap()
    private var siteConfigCache: SiteConfig? = null
    private var rankingCache: Pair<Long, Map<String, List<String>>>? = null

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "${domainResolver.currentBaseUrl}/")
        .set("Origin", domainResolver.currentBaseUrl)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.fromCallable {
        getPage(applyRule(savedRule(POPULAR_RULE_KEY, POPULAR_RULE)), page)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = Observable.fromCallable {
        getPage(applyRule(savedRule(LATEST_RULE_KEY, LATEST_RULE)), page)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.fromCallable {
            val rule = filters.toRule()
            if (page == 1) saveRule(filters.saveAction(), rule)
            var result = applyRule(rule)
            if (query.isNotBlank()) {
                val normalized = query.trim()
                result = result.filter {
                    it.title.contains(normalized, ignoreCase = true) ||
                        it.author.contains(normalized, ignoreCase = true)
                }
            }
            getPage(result, page)
        }

    override fun getFilterList(): FilterList = filterList(
        savedRule(POPULAR_RULE_KEY, POPULAR_RULE).summary(),
        savedRule(LATEST_RULE_KEY, LATEST_RULE).summary(),
        domainResolver.currentBaseUrl,
    )

    private fun applyRule(input: BlacktoonRule): List<SeriesItem> {
        val rule = input.normalized()
        if (rule.category == 2) {
            val byId = loadSeries(setOf(0, 1)).associateBy { it.id }
            return rankings()[rule.rankingKey()].orEmpty().mapNotNull(byId::get)
        }

        var result = loadSeries(setOf(rule.category))
        if (rule.category == 1 && rule.publishingDay != 0) {
            result = result.filter { it.publishingDay == rule.publishingDay }
        }
        if (rule.platform != -1) result = result.filter { it.platform == rule.platform }
        if (rule.tag != -1) result = result.filter { rule.tag in it.tagIdsList }
        result = when (rule.contentScope) {
            1 -> result.filter { 16 !in it.tagIdsList && 6 !in it.tagIdsList }
            2 -> result.filter { 16 in it.tagIdsList }
            3 -> result.filter { 6 in it.tagIdsList }
            else -> result
        }
        return if (rule.order == 1) result.sortedByDescending(SeriesItem::hot) else result.sortedByDescending(SeriesItem::updatedAt)
    }

    private fun getPage(items: List<SeriesItem>, page: Int): MangasPage {
        val start = (page - 1) * PAGE_SIZE
        if (start >= items.size) return MangasPage(emptyList(), false)
        val end = min(page * PAGE_SIZE, items.size)
        val config = loadSiteConfig()
        return MangasPage(items.subList(start, end).map { it.toSManga(config) }, end < items.size)
    }

    private fun loadSeries(indexes: Set<Int>): List<SeriesItem> = synchronized(cacheLock) {
        resetCachesIfDomainChanged()
        val missing = indexes.filterNot(seriesCache::containsKey)
        if (missing.isNotEmpty()) {
            val scripts = discoverSeriesScripts()
            val mutable = seriesCache.toMutableMap()
            for (index in missing) {
                val path = scripts[index] ?: error("Blacktoon series data script $index was not found")
                mutable[index] = loadSeriesScript(index, path)
            }
            seriesCache = mutable
        }
        indexes.sortedDescending().flatMap { seriesCache[it].orEmpty() }
    }

    private fun discoverSeriesScripts(): Map<Int, String> {
        val body = requestText(domainResolver.currentBaseUrl)
        return SERIES_SCRIPT.findAll(body).associate { match ->
            match.groupValues[2].toInt() to match.groupValues[1]
        }
    }

    private fun loadSeriesScript(index: Int, url: String): List<SeriesItem> {
        val config = loadSiteConfig()
        val path = url.trimStart('/')
        val body = requestTextWithFallback(config.seriesIncludeUrl + path, "${domainResolver.currentBaseUrl}/$path")
        val payload = body.substringAfter(" = ").trim().removeSuffix(";")
        return json.decodeFromString<List<SeriesItem>>(payload).onEach { it.listIndex = index }
    }

    private fun loadSiteConfig(): SiteConfig = synchronized(cacheLock) {
        resetCachesIfDomainChanged()
        siteConfigCache?.let { return@synchronized it }
        val fallback = SiteConfig(
            CDN_URL,
            CDN_URL,
            "${domainResolver.currentBaseUrl}/",
            "${domainResolver.currentBaseUrl}/",
            "${domainResolver.currentBaseUrl}/",
        )
        val config = runCatching {
            val text = client.newCall(GET("${domainResolver.currentBaseUrl}/data/config.js", headers)).execute().use { response ->
                check(response.isSuccessful) { "Blacktoon configuration request failed: ${response.code}" }
                response.body.string()
            }
            SiteConfig(
                configUrl(text, "img_domain"),
                configUrl(text, "img_domain8"),
                configUrl(text, "inc_url"),
                configUrl(text, "inc_url1"),
                configUrl(text, "inc_url2"),
            )
        }.getOrDefault(fallback)
        siteConfigCache = config
        config
    }

    private fun configUrl(script: String, name: String): String {
        val value = Regex("(?:^|[;\\r\\n])\\s*(?:(?:var|let|const)\\s+)?${Regex.escape(name)}\\s*=\\s*[\"']([^\"']+)[\"']")
            .findAll(script).lastOrNull()?.groupValues?.get(1)
            ?: error("Blacktoon configuration is missing $name")
        require(value.startsWith("https://") || value.startsWith("http://"))
        return value.trimEnd('/') + "/"
    }

    private fun rankings(): Map<String, List<String>> = synchronized(cacheLock) {
        resetCachesIfDomainChanged()
        val now = System.currentTimeMillis()
        rankingCache?.takeIf { now - it.first < TimeUnit.MINUTES.toMillis(5) }?.second?.let { return@synchronized it }
        val config = loadSiteConfig()
        val cacheBuster = "?v=$now"
        val body = requestTextWithFallback(
            config.rankingIncludeUrl + "data/top.js$cacheBuster",
            "${domainResolver.currentBaseUrl}/data/top.js$cacheBuster",
        )
        val parsed = TOP_HITS.findAll(body).associate { match ->
            match.groupValues[1] to match.groupValues[2].split(',').map(String::trim).filter(String::isNotBlank)
        }
        rankingCache = now to parsed
        parsed
    }

    private fun requestTextWithFallback(primary: String, fallback: String): String = runCatching {
        requestText(primary)
    }.getOrElse { requestText(fallback) }

    private fun requestText(url: String): String = client.newCall(GET(url, headers)).execute().use { response ->
        check(response.isSuccessful) { "Blacktoon data request failed: ${response.code}" }
        response.body.string()
    }

    private fun resetCachesIfDomainChanged() {
        val host = domainResolver.currentBaseUrl
        if (cacheHost == host) return
        cacheHost = host
        seriesCache = emptyMap()
        siteConfigCache = null
        rankingCache = null
    }

    private fun savedRule(key: String, fallback: BlacktoonRule): BlacktoonRule =
        BlacktoonRule.deserialize(readStringPreference(key), fallback)

    private fun readStringPreference(key: String): String? {
        val stored = preferences.all[key] ?: return null
        if (stored is String) return stored
        preferences.edit().remove(key).apply()
        return null
    }

    private fun saveRule(action: Int, rule: BlacktoonRule) {
        when (action) {
            1 -> preferences.edit().putString(POPULAR_RULE_KEY, rule.serialize()).apply()
            2 -> preferences.edit().putString(LATEST_RULE_KEY, rule.serialize()).apply()
            3 -> preferences.edit().remove(POPULAR_RULE_KEY).apply()
            4 -> preferences.edit().remove(LATEST_RULE_KEY).apply()
            5 -> preferences.edit().remove(POPULAR_RULE_KEY).remove(LATEST_RULE_KEY).apply()
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$baseUrl/webtoon/${manga.url}.html#${manga.status}", headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        description = document.select("p.mt-2").lastOrNull()?.text()
        status = response.request.url.fragment?.toIntOrNull() ?: SManga.UNKNOWN
    }

    override fun getMangaUrl(manga: SManga): String = "${domainResolver.currentBaseUrl}/webtoon/${manga.url}.html"

    override fun chapterListRequest(manga: SManga): Request =
        GET("${loadSiteConfig().chapterIncludeUrl}data/toonlist/${manga.url}.js?v=${Random.nextDouble()}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.pathSegments.last().removeSuffix(".js")
        val payload = response.body.string().substringAfter(" = ").trim().removeSuffix(";")
        return json.decodeFromString<List<Chapter>>(payload).map { it.toSChapter(mangaId) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "${domainResolver.currentBaseUrl}/webtoons/${chapter.url}.html"

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/webtoons/${chapter.url}.html", headers)

    override fun pageListParse(response: Response): List<Page> {
        val imageDomain = loadSiteConfig().imageDomain
        return response.asJsoup().select("#toon_content_imgs img").mapIndexed { index, element ->
            val path = element.attr("o_src").trimStart('/').replace("[", "%5B").replace("]", "%5D")
            Page(index, imageUrl = imageDomain + path)
        }
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun BlacktoonRule.rankingKey(): String {
        val period = listOf("d", "w", "m", "h")[popularPeriod]
        val type = listOf("comm", "19", "bl")[popularType]
        return "${period}_$type"
    }

    private companion object {
        const val PAGE_SIZE = 24
        const val CDN_URL = "https://blacktoonimg.com/"
        val SERIES_SCRIPT = Regex("['\"](/data/webtoon/webtoon_([01])_[^'\"?]+\\.js)(?:\\?[^'\"]*)?['\"]")
        val TOP_HITS = Regex("tophits\\[['\"]([dwmh]_(?:comm|19|bl))['\"]]\\s*=\\s*['\"]([^'\"]*)['\"]")
    }
}
