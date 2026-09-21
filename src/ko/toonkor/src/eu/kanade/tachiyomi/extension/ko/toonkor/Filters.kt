package eu.kanade.tachiyomi.extension.ko.toonkor

import eu.kanade.tachiyomi.source.model.Filter

internal const val WEBTOONS_PATH = "/%EC%9B%B9%ED%88%B0"
internal const val ONGOING_PATH = "/%EC%97%B0%EC%9E%AC"
internal const val COMPLETED_PATH = "/%EC%99%84%EA%B2%B0"

internal const val SORT_LATEST = "?fil=%EC%B5%9C%EC%8B%A0"
internal const val SORT_POPULAR = "?fil=%EC%9D%B8%EA%B8%B0"
internal const val SORT_GENRE = "?fil=%EC%84%B1%EC%9D%B8"
internal const val SORT_TITLE = "?fil=%EC%A0%9C%EB%AA%A9"

open class UriPartFilter(
    displayName: String,
    private val values: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, values.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = values[state].second
}

class StatusFilter :
    UriPartFilter(
        "연재 상태",
        arrayOf(
            "전체" to ONGOING_PATH,
            "연재" to ONGOING_PATH,
            "완결" to COMPLETED_PATH,
        ),
    )

class SortFilter :
    UriPartFilter(
        "정렬",
        arrayOf(
            "최신" to SORT_LATEST,
            "인기" to SORT_POPULAR,
            "장르" to SORT_GENRE,
            "제목" to SORT_TITLE,
        ),
    )

class GenreFilter :
    UriPartFilter(
        "장르",
        arrayOf(
            "전체" to "",
            "성인" to SORT_GENRE,
            "드라마" to "?fil=%EB%93%9C%EB%9D%BC%EB%A7%88",
            "판타지" to "?fil=%ED%8C%90%ED%83%80%EC%A7%80",
            "액션" to "?fil=%EC%95%A1%EC%85%98",
            "로맨스" to "?fil=%EB%A1%9C%EB%A7%A8%EC%8A%A4",
            "일상" to "?fil=%EC%9D%BC%EC%83%81",
            "개그" to "?fil=%EA%B0%9C%EA%B7%B8",
            "미스터리" to "?fil=%EB%AF%B8%EC%8A%A4%ED%84%B0%EB%A6%AC",
            "순정" to "?fil=%EC%88%9C%EC%A0%95",
            "스포츠" to "?fil=%EC%8A%A4%ED%8F%AC%EC%B8%A0",
            "BL" to "?fil=BL",
            "스릴러" to "?fil=%EC%8A%A4%EB%A6%B4%EB%9F%AC",
            "무협" to "?fil=%EB%AC%B4%ED%98%91",
            "학원" to "?fil=%ED%95%99%EC%9B%90",
            "공포" to "?fil=%EA%B3%B5%ED%8F%AC",
            "스토리" to "?fil=%EC%8A%A4%ED%86%A0%EB%A6%AC",
        ),
    )
