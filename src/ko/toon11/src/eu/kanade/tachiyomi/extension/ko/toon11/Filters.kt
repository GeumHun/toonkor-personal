package eu.kanade.tachiyomi.extension.ko.toon11

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal const val EXCLUDE_ADULT = "__exclude_adult__"

internal data class Toon11Rule(
    val listType: Int = 0,
    val latestGenre: String = "",
    val rankedGenre: String = "",
    val zipType: String = "",
) {
    fun normalized(): Toon11Rule = when (listType) {
        0 -> copy(rankedGenre = "", zipType = "")
        1, 2 -> copy(latestGenre = "", zipType = "")
        3 -> copy(latestGenre = "", rankedGenre = "", zipType = "")
        4 -> copy(latestGenre = "", rankedGenre = "")
        else -> Toon11Rule()
    }

    fun serialize(): String = normalized().let { rule ->
        listOf(rule.listType, rule.latestGenre, rule.rankedGenre, rule.zipType).joinToString("|")
    }

    fun label(): String {
        val rule = normalized()
        fun genre(value: String): String = when (value) {
            "" -> "장르 전체"
            EXCLUDE_ADULT -> "성인물 제외"
            "일상 치유" -> "장르 일상+치유"
            else -> "장르 $value"
        }
        return when (rule.listType) {
            0 -> "최신만화 / ${genre(rule.latestGenre)}"
            1 -> "인기만화 / ${genre(rule.rankedGenre)}"
            2 -> "완결만화 / ${genre(rule.rankedGenre)}"
            3 -> "매일 추천 100"
            4 -> "요청 ZIP / ${zipTypes[rule.zipType] ?: zipTypes.getValue("")}"
            else -> LATEST_RULE.label()
        }
    }

    companion object {
        fun deserialize(value: String?, fallback: Toon11Rule): Toon11Rule {
            if (value == null) return fallback
            val parts = value.split('|')
            if (parts.size != 4) return fallback
            val rule = Toon11Rule(parts[0].toIntOrNull() ?: return fallback, parts[1], parts[2], parts[3]).normalized()
            val validLatest = rule.latestGenre == EXCLUDE_ADULT || rule.latestGenre.isEmpty() || latestGenres.contains(rule.latestGenre)
            val validRanked = rule.rankedGenre == EXCLUDE_ADULT || rule.rankedGenre.isEmpty() || rankedGenres.contains(rule.rankedGenre)
            val validZip = zipTypes.containsKey(rule.zipType)
            return if (validLatest && validRanked && validZip) rule else fallback
        }
    }
}

private val listTypes = arrayOf("최신만화", "인기만화", "완결만화", "매일 추천 100", "요청 ZIP")

private val latestGenres = arrayOf(
    "성인물 제외",
    "전체",
    "SF",
    "무협",
    "TS",
    "개그",
    "드라마",
    "러브코미디",
    "먹방",
    "백합",
    "붕탁",
    "스릴러",
    "스포츠",
    "시대",
    "액션",
    "순정",
    "일상 치유",
    "추리",
    "판타지",
    "학원",
    "호러",
    "BL",
    "17",
    "이세계",
    "전생",
    "라노벨",
    "애니화",
    "TL",
    "공포",
    "하렘",
    "요리",
)

private val rankedGenres = arrayOf(
    "성인물 제외",
    "전체",
    "BL",
    "러브코미디",
    "17",
    "판타지",
    "순정",
    "드라마",
    "학원",
    "게임",
    "SF",
    "스릴러",
    "먹방",
    "TS",
    "스포츠",
    "이세계",
    "추리",
    "일상",
    "라노벨",
    "백합",
    "시대",
    "애니화",
    "전생",
    "붕탁",
    "무협",
    "호러",
    "공포",
)

private val zipTypes = linkedMapOf("" to "핫신작", "todayhit" to "매일 TOP 35", "isover" to "완결")

internal class ListTypeFilter : Filter.Select<String>("목록 종류", listTypes)

internal class LatestGenreFilter : Filter.Select<String>("최신만화 장르 (하나 선택)", latestGenres, 1) {
    val selected: String
        get() = when (state) {
            0 -> EXCLUDE_ADULT
            1 -> ""
            else -> latestGenres[state].replace("일상 치유", "일상+치유")
        }
}

internal class RankedGenreFilter : Filter.Select<String>("인기·완결 장르 (하나 선택)", rankedGenres, 1) {
    val selected: String
        get() = when (state) {
            0 -> EXCLUDE_ADULT
            1 -> ""
            else -> rankedGenres[state]
        }
}

internal class ZipTypeFilter : Filter.Select<String>("요청 ZIP 분류", zipTypes.values.toTypedArray()) {
    val selected: String
        get() = zipTypes.keys.elementAt(state)
}

internal class SaveRuleFilter : Filter.Select<String>(
    "Popular/Latest 규칙",
    arrayOf(
        "저장하지 않음 (필터 결과만 보기)",
        "현재 조건을 Popular 탭에 저장",
        "현재 조건을 Latest 탭에 저장",
        "Popular 탭을 기본값으로 복원",
        "Latest 탭을 기본값으로 복원",
        "두 탭 모두 기본값으로 복원",
    ),
)

internal fun filterList(popularLabel: String, latestLabel: String): FilterList = FilterList(
    Filter.Header("검색어와 목록 필터는 함께 사용할 수 없습니다."),
    Filter.Header("목록 종류에 맞지 않는 조건은 자동으로 무시됩니다."),
    ListTypeFilter(),
    Filter.Separator(),
    Filter.Header("최신만화 조건"),
    LatestGenreFilter(),
    Filter.Separator(),
    Filter.Header("인기·완결 조건"),
    RankedGenreFilter(),
    Filter.Separator(),
    Filter.Header("요청 ZIP 조건"),
    ZipTypeFilter(),
    Filter.Separator(),
    Filter.Header("조건을 고른 뒤 Filter를 누르면 저장됩니다."),
    Filter.Header("현재 Popular: $popularLabel"),
    Filter.Header("현재 Latest: $latestLabel"),
    SaveRuleFilter(),
)

internal fun FilterList.toRule(): Toon11Rule {
    var listType = 0
    var latestGenre = ""
    var rankedGenre = ""
    var zipType = ""
    for (filter in this) {
        when (filter) {
            is ListTypeFilter -> listType = filter.state
            is LatestGenreFilter -> latestGenre = filter.selected
            is RankedGenreFilter -> rankedGenre = filter.selected
            is ZipTypeFilter -> zipType = filter.selected
        }
    }
    return Toon11Rule(listType, latestGenre, rankedGenre, zipType).normalized()
}

internal fun FilterList.saveAction(): Int = filterIsInstance<SaveRuleFilter>().firstOrNull()?.state ?: 0

internal val LATEST_RULE = Toon11Rule(listType = 0)
internal val POPULAR_RULE = Toon11Rule(listType = 1)
