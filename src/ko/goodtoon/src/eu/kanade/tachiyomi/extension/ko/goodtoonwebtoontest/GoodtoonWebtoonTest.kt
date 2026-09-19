package eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class GoodtoonWebtoonTest : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET(pageUrl("/recommend/", page), headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response.asJsoup())

    override fun latestUpdatesRequest(page: Int): Request = GET(pageUrl("/", page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("pg", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response.asJsoup())

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("a.card").mapNotNull { element ->
            val title = element.selectFirst(".subject")?.text()?.trim().orEmpty()
            val url = element.attr("abs:href")
            if (title.isEmpty() || url.isEmpty()) return@mapNotNull null

            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(url)
                thumbnail_url = element.selectFirst(".thumb img:not(.platform-icon)")?.absUrl("src")
                    ?.ifEmpty { null }
            }
        }
        val hasNextPage = document.select(".pagination a").any { it.text().contains("다음") }
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".summary-title h1, h1")?.text()?.trim().orEmpty()
            author = document.selectFirst(".manga-summary-author")?.text()?.trim().orEmpty()
            description = document.selectFirst(".manga-summary-desc")?.text()?.trim().orEmpty()
            val genreElements = document.select(".manga-summary-genres a").ifEmpty {
                document.select(".manga-summary-genres")
            }
            genre = genreElements
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
            thumbnail_url = document.selectFirst(".manga-summary-cover img")?.absUrl("src")
                ?.ifEmpty { null }
            status = parseStatus(document.selectFirst(".summary-meta-row")?.text().orEmpty())
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("li.wp-manga-chapter")
        .mapNotNull { element -> chapterFromElement(element) }

    private fun chapterFromElement(element: Element): SChapter? {
        val link = element.selectFirst("a") ?: return null
        val url = link.absUrl("href")
        val name = link.text().trim()
        if (url.isEmpty() || name.isEmpty()) return null

        return SChapter.create().apply {
            this.url = url.removePrefix(baseUrl)
            this.name = name
            date_upload = parseDate(element.selectFirst(".chapter-release-date")?.text().orEmpty())
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".reading-content img.wp-manga-chapter-img")
        .mapIndexedNotNull { index, image ->
            image.absUrl("src").ifEmpty { image.absUrl("data-src") }.ifEmpty { null }?.let { url ->
                Page(index, "", url, null)
            }
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    private fun pageUrl(path: String, page: Int): String {
        val url = baseUrl + path
        return if (page <= 1) url else "$url?pg=$page"
    }

    private fun parseDate(value: String): Long {
        return try {
            SimpleDateFormat("yy.MM.dd", Locale.ROOT).parse(value.trim())?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseStatus(value: String): Int = when {
        value.contains("완결") -> SManga.COMPLETED
        value.contains("연재") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }
}
