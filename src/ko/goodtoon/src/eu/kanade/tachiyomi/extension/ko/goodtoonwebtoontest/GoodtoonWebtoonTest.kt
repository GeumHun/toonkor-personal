package eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Source
abstract class GoodtoonWebtoonTest : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = listRequest("/", page, category = "webtoon", day = currentDay())

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response.asJsoup())

    override fun latestUpdatesRequest(page: Int): Request = listRequest("/end/", page)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val activeFilters = if (filters.isEmpty()) getFilterList() else filters
        var category = "all"
        var day = "all"
        var platform = ""
        for (filter in activeFilters) {
            when (filter) {
                is CategoryFilter -> category = filter.value()
                is DayFilter -> day = filter.value()
                is PlatformFilter -> platform = filter.value()
                else -> Unit
            }
        }
        return listRequest("/", page, query, category, day, platform)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response.asJsoup())

    private fun listRequest(path: String, page: Int, query: String = "", category: String = "all", day: String = "all", platform: String = ""): Request {
        val builder = baseUrl.toHttpUrl().newBuilder().encodedPath(path)
        if (query.isNotBlank()) builder.addQueryParameter("q", query.trim())
        if (category != "all") builder.addQueryParameter("mcat", category)
        if (day != "all") builder.addQueryParameter("mday", day)
        if (platform.isNotBlank()) builder.addQueryParameter("plat", platform)
        builder.addQueryParameter("pg", page.toString())
        return GET(builder.build(), headers)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = ArrayList<SManga>()
        val seenUrls = HashSet<String>()
        for (element in document.select("a.card")) {
            val url = element.absUrl("href")
            val title = element.selectFirst(".subject")?.text()?.trim().orEmpty()
            if (url.isEmpty() || title.isEmpty()) continue
            val manga = SManga.create().apply {
                this.url = url.toHttpUrl().encodedPath.ensureTrailingSlash()
                this.title = title
                thumbnail_url = imageUrl(element.selectFirst(".thumb img:not(.platform-icon)"))
            }
            if (seenUrls.add(manga.url)) mangas.add(manga)
        }
        return MangasPage(mangas, document.select("a.page-numbers").any { it.text().contains("다음") })
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val metadata = document.selectFirst(".summary-meta-row .meta-value")?.text().orEmpty()
        return SManga.create().apply {
            title = document.selectFirst(".summary-title")?.text()?.trim().orEmpty()
            thumbnail_url = imageUrl(document.selectFirst(".manga-summary-cover img"))
            author = document.selectFirst(".manga-summary-author .author-text")?.text()?.trim().orEmpty()
            genre = document.selectFirst(".manga-summary-genres")?.text()?.trim().orEmpty()
            description = document.selectFirst(".manga-summary-desc")?.text()?.trim().orEmpty()
            status = when {
                metadata.contains("완결") -> SManga.COMPLETED
                metadata.contains("연재") || metadata.contains("웹툰") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = POST(baseUrl + manga.url.trimEnd('/') + "/ajax/chapters/", headers, FormBody.Builder().build())

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = ArrayList<SChapter>()
        val seenUrls = HashSet<String>()
        for (element in response.asJsoup().select("li.wp-manga-chapter")) {
            val link = element.selectFirst("a") ?: continue
            val url = link.absUrl("href")
            val name = link.text().trim()
            if (url.isEmpty() || name.isEmpty()) continue
            val chapter = SChapter.create().apply {
                setUrlWithoutDomain(url)
                this.name = name
                date_upload = parseDate(element.selectFirst(".chapter-release-date")?.text().orEmpty())
            }
            if (seenUrls.add(chapter.url)) chapters.add(chapter)
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val urls = ArrayList<String>()
        for (element in response.asJsoup().select(".reading-content img, div.page-break img")) {
            val url = imageUrl(element) ?: continue
            if (!urls.contains(url)) urls.add(url)
        }
        return urls.mapIndexed { index, url -> Page(index, "", url, null) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
        .set("Referer", "$baseUrl/")

    override fun getFilterList(): FilterList = FilterList(CategoryFilter(), DayFilter(), PlatformFilter())

    private fun imageUrl(element: Element?): String? = element?.absUrl("data-lazy-src")
        ?.ifEmpty { element.absUrl("src") }
        ?.ifEmpty { element.absUrl("data-src") }
        ?.ifEmpty { null }

    private fun parseDate(value: String): Long = try {
        dateFormat.parse(value.replace(" ", ""))?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    private fun currentDay(): String = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "mon"
        Calendar.TUESDAY -> "tue"
        Calendar.WEDNESDAY -> "wed"
        Calendar.THURSDAY -> "thu"
        Calendar.FRIDAY -> "fri"
        Calendar.SATURDAY -> "sat"
        Calendar.SUNDAY -> "sun"
        else -> "etc"
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private open class GoodtoonFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
        fun value(): String = options[state].second
    }

    private class CategoryFilter : GoodtoonFilter("분류", arrayOf("전체" to "all", "일반웹툰" to "webtoon", "BL/GL" to "bl-gl", "성인웹툰" to "adult"))

    private class DayFilter : GoodtoonFilter("요일", arrayOf("전체" to "all", "월" to "mon", "화" to "tue", "수" to "wed", "목" to "thu", "금" to "fri", "토" to "sat", "일" to "sun", "열흘" to "etc"))

    private class PlatformFilter : GoodtoonFilter("플랫폼", arrayOf(
        "전체" to "", "네이버" to "naver", "다음" to "daum", "카카오" to "kakao", "레진" to "rejin", "투믹스" to "tomics", "탑툰" to "toptoon", "코미카" to "comica", "배틀코믹스" to "battlecomics", "코믹GT" to "comicgt", "케이툰" to "ktoon", "애니툰" to "anitoon", "폭스툰" to "foxtoon", "피너툰" to "peanutoon", "봄툰" to "bom", "코미코" to "comico", "무툰" to "mootoon",
    ))

    private companion object {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT)
    }
}
