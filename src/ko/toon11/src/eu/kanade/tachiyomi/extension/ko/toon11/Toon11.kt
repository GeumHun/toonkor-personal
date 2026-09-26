package eu.kanade.tachiyomi.extension.ko.toon11

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Locale
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Source
abstract class Toon11 : HttpSource() {

    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    override val client = network.client.newBuilder()
        .addInterceptor(::imageCandidateInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = listRequest(savedRule(POPULAR_RULE_KEY, POPULAR_RULE), page)

    override fun popularMangaParse(response: Response): MangasPage = listParse(response.asJsoup(), response)

    override fun latestUpdatesRequest(page: Int): Request = listRequest(savedRule(LATEST_RULE_KEY, LATEST_RULE), page)

    override fun latestUpdatesParse(response: Response): MangasPage = listParse(response.asJsoup(), response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/bbs/search_stx.php".toHttpUrl().newBuilder()
                .addQueryParameter("stx", query)
                .build()
            return GET(url, headers)
        }
        val rule = filters.toRule()
        if (page == 1) saveRule(filters.saveAction(), rule)
        return listRequest(rule, page)
    }

    private fun savedRule(key: String, fallback: Toon11Rule): Toon11Rule =
        Toon11Rule.deserialize(preferences.getString(key, null), fallback)

    private fun saveRule(action: Int, rule: Toon11Rule) {
        when (action) {
            1 -> preferences.edit().putString(POPULAR_RULE_KEY, rule.serialize()).apply()
            2 -> preferences.edit().putString(LATEST_RULE_KEY, rule.serialize()).apply()
            3 -> preferences.edit().remove(POPULAR_RULE_KEY).apply()
            4 -> preferences.edit().remove(LATEST_RULE_KEY).apply()
            5 -> preferences.edit().remove(POPULAR_RULE_KEY).remove(LATEST_RULE_KEY).apply()
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return if (response.request.url.encodedPath.endsWith("/bbs/board.php")) {
            listParse(document, response)
        } else {
            val mangas = ArrayList<SManga>()
            for (element in document.select("li[data-id]")) {
                parseListItem(element)?.let(mangas::add)
            }
            MangasPage(mangas, false)
        }
    }

    private fun listRequest(rule: Toon11Rule, page: Int): Request {
        val normalized = rule.normalized()
        val excludeAdult = normalized.latestGenre == EXCLUDE_ADULT || normalized.rankedGenre == EXCLUDE_ADULT
        val builder = "$baseUrl/bbs/board.php".toHttpUrl().newBuilder()
            .addQueryParameter("bo_table", "toon_c")

        when (normalized.listType) {
            0 -> {
                builder.addQueryParameter("sord", "")
                builder.addQueryParameter("type", "upd")
                if (normalized.latestGenre.isNotEmpty() && !excludeAdult) {
                    builder.addQueryParameter("sca", normalized.latestGenre)
                    builder.addQueryParameter("tablename", "최신만화")
                }
            }
            1 -> {
                if (normalized.rankedGenre.isNotEmpty() && !excludeAdult) {
                    builder.addQueryParameter("sca", normalized.rankedGenre)
                    builder.addQueryParameter("tablename", "인기만화")
                }
                builder.addQueryParameter("is_over", "0")
            }
            2 -> {
                if (normalized.rankedGenre.isNotEmpty() && !excludeAdult) {
                    builder.addQueryParameter("sca", normalized.rankedGenre)
                    builder.addQueryParameter("tablename", "완결만화")
                }
                builder.addQueryParameter("is_over", "1")
            }
            3 -> {
                builder.addQueryParameter("type", "today")
                builder.addQueryParameter("tablename", "매일 추천 100")
            }
            4 -> {
                builder.addQueryParameter("type", "invite")
                builder.addQueryParameter("tablename", "요청 Zip")
                if (normalized.zipType.isNotEmpty()) builder.addQueryParameter("types", normalized.zipType)
            }
        }
        if (page > 1) builder.addQueryParameter("page", page.toString())
        val requestHeaders = if (excludeAdult) headers.newBuilder().set(EXCLUDE_ADULT_HEADER, "1").build() else headers
        return GET(builder.build(), requestHeaders)
    }

    private fun listParse(document: Document, response: Response): MangasPage {
        val excludeAdult = response.request.header(EXCLUDE_ADULT_HEADER) == "1"
        val mangas = ArrayList<SManga>()
        for (element in document.select("li[data-id]")) {
            if (excludeAdult && isAdult(element)) continue
            parseListItem(element)?.let(mangas::add)
        }
        return MangasPage(mangas, document.selectFirst(".pg_end") != null)
    }

    private fun parseListItem(element: Element): SManga? {
        val id = element.attr("data-id")
        if (id.isEmpty() || id.any { !it.isDigit() }) return null
        val title = element.selectFirst(".homelist-title")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null
        return SManga.create().apply {
            this.title = title
            url = mangaUrl(id)
            thumbnail_url = thumbnailUrl(element)
        }
    }

    private fun mangaUrl(id: String): String = "/bbs/board.php?bo_table=toons&is=$id"

    private fun thumbnailUrl(element: Element): String? {
        val thumb = element.selectFirst(".homelist-thumb") ?: return null
        val mobile = thumb.absUrl("data-mobile-image")
        if (mobile.isNotBlank()) return mobile
        val path = backgroundImageRegex.find(thumb.attr("style"))?.groupValues?.get(1).orEmpty()
        return when {
            path.startsWith("//") -> "https:$path"
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> null
        }
    }

    private fun isAdult(element: Element): Boolean {
        val genre = element.selectFirst(".homelist-genre")?.text().orEmpty()
        return adultGenreRegex.containsMatchIn(genre)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = (baseUrl + manga.url).toHttpUrl().newBuilder()
        if (manga.title.isNotBlank()) url.setQueryParameter("stx", manga.title)
        return GET(url.build(), headers)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2.title")?.text().orEmpty()
            thumbnail_url = document.selectFirst("img.banner")?.absUrl("src")
            status = parseStatus(field(document, "분류").orEmpty())
            author = field(document, "작가")
            description = field(document, "소개")
            genre = field(document, "장르")?.split(',')?.joinToString { it.trim() }
        }
    }

    private fun field(document: Document, label: String): String? = document.selectFirst("span:contains($label) + span")?.text()

    private fun parseStatus(value: String): Int = when {
        "완결" in value -> SManga.COMPLETED
        statusOngoingRegex.containsMatchIn(value) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = LinkedHashMap<String, SChapter>()
        parseChapterPage(document, chapters)
        var next = document.selectFirst("span.pg .pg_current ~ .pg_page")?.absUrl("href")
        while (!next.isNullOrBlank()) {
            val nextDocument = client.newCall(GET(next, headers)).execute().asJsoup()
            parseChapterPage(nextDocument, chapters)
            next = nextDocument.selectFirst("span.pg .pg_current ~ .pg_page")?.absUrl("href")
        }
        return chapters.values.toList()
    }

    private fun parseChapterPage(document: Document, chapters: LinkedHashMap<String, SChapter>) {
        for (element in document.select("#comic-episode-list > li")) {
            val chapter = parseChapter(element) ?: continue
            if (!chapters.containsKey(chapter.url)) chapters[chapter.url] = chapter
        }
    }

    private fun parseChapter(element: Element): SChapter? {
        val button = element.selectFirst("button[onclick]") ?: return null
        val relative = button.attr("onclick").substringAfter("location.href='.", "").substringBefore("'")
        if (relative.isEmpty()) return null
        val url = (baseUrl + "/bbs" + relative).toHttpUrl()
        val chapterId = url.queryParameter("wr_id")?.takeIf { value -> value.all(Char::isDigit) } ?: return null
        val mangaId = url.queryParameter("is")?.takeIf { value -> value.all(Char::isDigit) } ?: return null
        val rawName = button.selectFirst(".episode-title")?.text()?.trim().orEmpty()
        val numberMatch = episodeNumberRegex.findAll(rawName).lastOrNull()
        return SChapter.create().apply {
            this.url = "/board.php?bo_table=toons&wr_id=$chapterId&is=$mangaId"
            name = normalizeChapterName(rawName, numberMatch)
            chapter_number = numberMatch?.let(::chapterNumber) ?: -1f
            date_upload = dateFormat.tryParse(element.selectFirst(".free-date")?.text())
        }
    }

    private fun normalizeChapterName(name: String, match: MatchResult?): String {
        if (match == null) return name
        val prefix = name.substring(0, match.range.first)
        return if (prefix.any(Char::isDigit)) name.substring(match.range.first).trim() else name
    }

    private fun chapterNumber(match: MatchResult): Float {
        val major = match.groupValues[1].toFloatOrNull() ?: return -1f
        val rangeEnd = match.groupValues[2].toIntOrNull()
        if (rangeEnd != null) return major + rangeEnd.coerceIn(1, 999) / 1000f
        val decimal = match.groupValues[3]
        return if (decimal.isEmpty()) major else "$major.$decimal".toFloatOrNull() ?: major
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + "/bbs" + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val script = response.asJsoup().selectFirst("script:containsData(img_list)")?.data()
            ?: throw IllegalStateException("이미지 목록 스크립트를 찾지 못했습니다.")
        val primary = extractList(script, imgListRegex)
        val secondary = extractList(script, imgList2Regex)
        val pages = ArrayList<Page>(maxOf(primary.size, secondary.size))
        for (index in 0 until maxOf(primary.size, secondary.size)) {
            val candidates = linkedSetOf<String>()
            primary.getOrNull(index)?.let { candidates.add(normalizeImageUrl(it)) }
            secondary.getOrNull(index)?.let { candidates.add(normalizeImageUrl(it)) }
            candidates.remove("")
            if (candidates.isNotEmpty()) {
                pages.add(Page(index, candidates.joinToString(IMAGE_SEPARATOR), candidates.first()))
            }
        }
        return pages
    }

    private fun extractList(script: String, regex: Regex): List<String> =
        regex.find(script)?.groupValues?.get(1)?.parseAs<List<String>>() ?: emptyList()

    private fun normalizeImageUrl(value: String): String = if (value.startsWith("//")) "https:$value" else value

    override fun imageRequest(page: Page): Request {
        val candidates = page.url.split(IMAGE_SEPARATOR).filter(String::isNotBlank).ifEmpty { listOfNotNull(page.imageUrl) }
        return GET(candidates.first(), headers.newBuilder().set(IMAGE_CANDIDATES_HEADER, candidates.joinToString(IMAGE_SEPARATOR)).build())
    }

    private fun imageCandidateInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val candidates = request.header(IMAGE_CANDIDATES_HEADER)
            ?.split(IMAGE_SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (candidates.isEmpty()) return chain.proceed(request)
        val clean = request.newBuilder().removeHeader(IMAGE_CANDIDATES_HEADER).build()
        var lastResponse: Response? = null
        var lastError: Exception? = null
        for (candidate in candidates) {
            lastResponse?.close()
            try {
                val response = chain.proceed(clean.newBuilder().url(candidate).build())
                if (response.isSuccessful) return response
                lastResponse = response
            } catch (error: Exception) {
                lastError = error
                lastResponse = null
            }
        }
        return lastResponse ?: throw lastError ?: IllegalStateException("이미지 주소에 연결하지 못했습니다.")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = filterList(
        savedRule(POPULAR_RULE_KEY, POPULAR_RULE).label(),
        savedRule(LATEST_RULE_KEY, LATEST_RULE).label(),
    )

    companion object {
        private const val POPULAR_RULE_KEY = "toon11_popular_rule_v1"
        private const val LATEST_RULE_KEY = "toon11_latest_rule_v1"
        private const val EXCLUDE_ADULT_HEADER = "X-Toon11-Exclude-Adult"
        private const val IMAGE_CANDIDATES_HEADER = "X-Toon11-Image-Candidates"
        private const val IMAGE_SEPARATOR = "|"
        private val dateFormat = SimpleDateFormat("yy.MM.dd", Locale.ENGLISH)
        private val imgListRegex = """img_list\s*=\s*(\[.*?])""".toRegex(RegexOption.DOT_MATCHES_ALL)
        private val imgList2Regex = """img_list_2\s*=\s*(\[.*?])""".toRegex(RegexOption.DOT_MATCHES_ALL)
        private val backgroundImageRegex = """url\(['"]?([^'")]+)""".toRegex()
        private val episodeNumberRegex = """(\d+)(?:\s*[-–—]\s*(\d+)|[.,](\d+))?\s*(?:화|회)(?:\D|$)""".toRegex()
        private val adultGenreRegex = """(?:^|[,\s])(?:BL|러브코미디|17|순정|TS|백합|붕탁)(?=$|[,\s])""".toRegex(RegexOption.IGNORE_CASE)
        private val statusOngoingRegex = """주간|월간|연재|격주""".toRegex()
    }
}
