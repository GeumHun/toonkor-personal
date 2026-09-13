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
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

@Source
abstract class Toonkor : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_POPULAR", headers)

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
            manga.thumbnail_url = element.select("img").attr("abs:src")
            mangas.add(manga)
            index++
        }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_LATEST", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList: FilterList
        if (filters.isEmpty()) {
            filterList = getFilterList()
        } else {
            filterList = filters
        }

        var type: TypeFilter? = null
        var status: StatusFilter? = null
        var sort: SortFilter? = null
        var index = 0

        while (index < filterList.size) {
            val filter: Filter<*> = filterList.get(index)
            if (filter is TypeFilter) {
                type = filter
            } else if (filter is StatusFilter) {
                status = filter
            } else if (filter is SortFilter) {
                sort = filter
            }
            index++
        }

        val requestPath: String
        if (query.length > 0) {
            requestPath = "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=" + query
        } else {
            val pathBuilder = StringBuilder()
            if (type != null) {
                pathBuilder.append(type.toUriPart())
            }
            if (status != null) {
                pathBuilder.append(status.toUriPart())
            }
            if (sort != null) {
                pathBuilder.append(sort.toUriPart())
            }
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
            manga.thumbnail_url = thumbnailElements.get(0).attr("abs:src")
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
        val decoded: String = String(decodedBytes)
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
        Filter.Header("Note: can't combine with text search!"),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val quotedValuePattern = Pattern.compile("^[^']*'([^']*)(?:'|$)")
        private val pageListPattern = Pattern.compile("src=\"([^\"]*)\"")
    }
}
