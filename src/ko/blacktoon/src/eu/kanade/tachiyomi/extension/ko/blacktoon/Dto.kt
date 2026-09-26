package eu.kanade.tachiyomi.extension.ko.blacktoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
internal class SeriesItem(
    @SerialName("x") val id: String,
    @SerialName("t") val title: String,
    @SerialName("p") val poster: String = "",
    @SerialName("au") val author: String = "",
    @SerialName("g") val updatedAt: Long = 0,
    @SerialName("tag") private val tagIds: String = "",
    @SerialName("c") private val platformId: String = "-1",
    @SerialName("pd") private val publishingDayId: String = "-1",
    @SerialName("h") val hot: Int = 0,
    @SerialName("up") private val imageDomainIndex: String = "0",
) {
    var listIndex: Int = -1

    val tagIdsList: List<Int>
        get() = tagIds.split(',').mapNotNull(String::toIntOrNull)

    val platform: Int
        get() = platformId.toIntOrNull() ?: -1

    val publishingDay: Int
        get() = publishingDayId.toIntOrNull() ?: -1

    fun toSManga(config: SiteConfig): SManga = SManga.create().apply {
        url = id
        title = this@SeriesItem.title
        thumbnail_url = poster.takeIf(String::isNotBlank)?.let {
            val imageBase = if (imageDomainIndex == "2") config.alternateImageDomain else config.imageDomain
            imageBase + it.replace("_x4", "").replace("_x3", "").trimStart('/')
        }
        author = this@SeriesItem.author
        genre = buildList {
            add(platforms[platform])
            add(publishingDays[publishingDay])
            tagIdsList.forEach { add(tags[it]) }
        }.filterNotNull().joinToString()
        status = when (listIndex) {
            0 -> SManga.COMPLETED
            1 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
internal class Chapter(
    @SerialName("id") private val id: String,
    @SerialName("t") private val title: String,
    @SerialName("d") private val date: String = "",
) {
    fun toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        url = "$mangaId/$id"
        name = title
        date_upload = runCatching { dateFormat.parse(date)?.time ?: 0L }.getOrDefault(0L)
    }
}

internal data class SiteConfig(
    val imageDomain: String,
    val alternateImageDomain: String,
    val rankingIncludeUrl: String,
    val chapterIncludeUrl: String,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
