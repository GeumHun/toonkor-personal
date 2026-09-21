package eu.kanade.tachiyomi.extension.ko.toonkor

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

@Source
abstract class Toonkor : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$WEBTOONS_PATH$ONGOING_PATH$SORT_POPULAR", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document: Document = response.asJsoup()
        val elements: Elements = document.select("div.section-item-inner")
        val mangas: ArrayList<SManga> = ArrayList(elements.size)
        var index = 0

        while (index < elements.size) {
            val element: Element = elements.get(index)
            val titleElement: Elements = element.select("div.section-item-title a")
            val manga: SManga = SManga.create()

            manga.title = titleElement.select("h3").text()
            manga.setUrlWithoutDomain(titleElement.attr("abs:href"))
            manga.thumbnail_url = thumbnailUrl(element)
            mangas.add(manga)
            index++
        }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl$WEBTOONS_PATH$ONGOING_PATH$SORT_LATEST", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList: FilterList
        if (filters.isEmpty()) {
            filterList = getFilterList()
        } else {
            filterList = filters
        }

        var status: StatusFilter? = null
        var sort: SortFilter? = null
        var genre: GenreFilter? = null
        var index = 0

        while (index < filterList.size) {
            val filter: Filter<*> = filterList.get(index)
            if (filter is StatusFilter) {
                status = filter
            } else if (filter is SortFilter) {
                sort = filter
            } else if (filter is GenreFilter) {
                genre = filter
            }
            index++
        }

        val requestPath: String
        if (query.length > 0) {
            requestPath = "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=" + query
        } else {
            val pathBuilder = StringBuilder(WEBTOONS_PATH)
            pathBuilder.append(status?.toUriPart() ?: ONGOING_PATH)
            val genrePart = genre?.toUriPart().orEmpty()
            pathBuilder.append(if (genrePart.isNotEmpty()) genrePart else sort?.toUriPart().orEmpty())
            requestPath = pathBuilder.toString()
        }

        return GET(baseUrl + requestPath, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document: Document = response.asJsoup()
        val details: Elements = document.select("table.bt_view1")
        val manga: SManga = SManga.create()
        val thumbnailElements: Elements = details.select("td.bt_thumb img")

        manga.title = details.select("td.bt_title").text()
        manga.author = details.select("td.bt_label span.bt_data").text()
        manga.description = details.select("td.bt_over").text()
        if (thumbnailElements.isEmpty()) {
            manga.thumbnail_url = null
        } else {
            manga.thumbnail_url = thumbnailUrl(thumbnailElements.get(0))
        }

        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return ToonkorChapterParser.parse(document, dateFormat)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document: Document = response.asJsoup()
        val scriptElements: Elements = document.select("script:containsData(toon_img)")
        val pages: ArrayList<Page> = ArrayList()

        if (scriptElements.isEmpty()) {
            return pages
        }

        val scriptData: String = scriptElements.get(0).data()
        val encodedMatcher: Matcher = quotedValuePattern.matcher(scriptData)
        var encoded: String = scriptData
        if (encodedMatcher.find()) {
            encoded = encodedMatcher.group(1)
        }

        val decodedBytes: ByteArray = Base64.decode(encoded, Base64.DEFAULT)
        val decoded: String = String(decodedBytes, StandardCharsets.UTF_8)
        val matcher: Matcher = pageListPattern.matcher(decoded)
        var pageIndex = 0

        while (matcher.find()) {
            val imagePath: String = matcher.group(1)
            val imageUrl: String
            if (
                imagePath.length >= 4 &&
                imagePath[0] == 'h' &&
                imagePath[1] == 't' &&
                imagePath[2] == 't' &&
                imagePath[3] == 'p'
            ) {
                imageUrl = imagePath
            } else {
                imageUrl = baseUrl + imagePath
            }
            pages.add(Page(pageIndex, "", imageUrl, null))
            pageIndex++
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("검색어와 필터는 함께 적용할 수 없습니다."),
        Filter.Separator(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    private fun thumbnailUrl(element: Element): String? {
        val image = if (element.tagName() == "img") element else element.selectFirst("img") ?: return null
        // The site's lazy images use a base64 placeholder in src and the real path in data-src.
        val attribute = if (image.hasAttr("data-src")) "data-src" else "src"
        val value = image.attr(attribute)
        if (value.isEmpty() || value.startsWith("data:")) return null
        return image.absUrl(attribute)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val quotedValuePattern = Pattern.compile("^[^']*'([^']*)(?:'|$)")
        private val pageListPattern = Pattern.compile("src=\"([^\"]*)\"")
    }
}
