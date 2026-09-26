package eu.kanade.tachiyomi.extension.ko.blacktoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal data class BlacktoonRule(
    val category: Int = 1,
    val publishingDay: Int = 0,
    val platform: Int = -1,
    val tag: Int = -1,
    val contentScope: Int = 0,
    val order: Int = 0,
    val popularPeriod: Int = 0,
    val popularType: Int = 0,
) {
    fun normalized(): BlacktoonRule = when (category) {
        0 -> copy(publishingDay = 0, popularPeriod = 0, popularType = 0)
        2 -> POPULAR_RULE.copy(
            popularPeriod = popularPeriod.coerceIn(0, 3),
            popularType = popularType.coerceIn(0, 2),
        )
        else -> copy(popularPeriod = 0, popularType = 0)
    }

    fun serialize(): String = normalized().let {
        listOf(it.category, it.publishingDay, it.platform, it.tag, it.contentScope, it.order, it.popularPeriod, it.popularType).joinToString("|")
    }

    companion object {
        fun deserialize(value: String?, fallback: BlacktoonRule): BlacktoonRule {
            val values = value?.split('|')?.mapNotNull(String::toIntOrNull) ?: return fallback
            if (values.size != 8) return fallback
            return BlacktoonRule(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]).normalized()
        }
    }
}

internal const val POPULAR_RULE_KEY = "blacktoon_popular_rule_v1"
internal const val LATEST_RULE_KEY = "blacktoon_latest_rule_v1"
internal val POPULAR_RULE = BlacktoonRule(category = 2)
internal val LATEST_RULE = BlacktoonRule(category = 1)

private open class PairSelect(name: String, private val entries: List<Pair<Int, String>>) :
    Filter.Select<String>(name, entries.map { it.second }.toTypedArray()) {
    val selected: Int get() = entries[state].first
}

private class ListTypeFilter : PairSelect("목록 종류", listOf(1 to "연재", 0 to "완결", 2 to "인기"))
private class PublishingDayFilter : PairSelect("요일 (연재 전용)", listOf(0 to "UP") + publishingDays.toList())
private class PlatformFilter : PairSelect("플랫폼 (연재·완결 전용)", listOf(-1 to "전체") + platforms.toList())
private class TagFilter : PairSelect("장르 (연재·완결 전용)", listOf(-1 to "전체") + tags.toList())
private class ContentScopeFilter : PairSelect(
    "콘텐츠 범위 (연재·완결 전용)",
    listOf(0 to "전체", 1 to "일반 작품만", 2 to "성인 작품", 3 to "BL/백합 작품"),
)
private class OrderFilter : PairSelect("정렬 (연재·완결 전용)", listOf(0 to "최신순", 1 to "인기순"))
private class PopularPeriodFilter : PairSelect(
    "인기 기간 (인기 전용)",
    listOf(0 to "일간BEST", 1 to "주간BEST", 2 to "월간BEST", 3 to "실시간BEST"),
)
private class PopularTypeFilter : PairSelect(
    "인기 분류 (인기 전용)",
    listOf(0 to "일반웹툰", 1 to "성인웹툰", 2 to "BL/백합"),
)
private class SaveActionFilter : Filter.Select<String>(
    "저장 동작",
    arrayOf("저장 안 함", "Popular에 저장", "Latest에 저장", "Popular 초기화", "Latest 초기화", "모두 초기화"),
)

internal fun filterList(popularSummary: String, latestSummary: String, address: String) = FilterList(
    Filter.Header("목록 종류에 맞지 않는 조건은 자동으로 무시됩니다."),
    ListTypeFilter(),
    Filter.Separator(),
    Filter.Header("연재 조건"),
    PublishingDayFilter(),
    Filter.Separator(),
    Filter.Header("연재·완결 공통 조건"),
    PlatformFilter(),
    TagFilter(),
    ContentScopeFilter(),
    OrderFilter(),
    Filter.Separator(),
    Filter.Header("인기 조건"),
    PopularPeriodFilter(),
    PopularTypeFilter(),
    Filter.Separator(),
    Filter.Header("조건을 고른 뒤 Filter를 누르면 저장됩니다."),
    Filter.Header("현재 Popular: $popularSummary"),
    Filter.Header("현재 Latest: $latestSummary"),
    SaveActionFilter(),
    Filter.Separator(),
    Filter.Header("현재 주소: $address"),
)

internal fun FilterList.toRule(): BlacktoonRule {
    var rule = BlacktoonRule()
    for (filter in this) {
        rule = when (filter) {
            is ListTypeFilter -> rule.copy(category = filter.selected)
            is PublishingDayFilter -> rule.copy(publishingDay = filter.selected)
            is PlatformFilter -> rule.copy(platform = filter.selected)
            is TagFilter -> rule.copy(tag = filter.selected)
            is ContentScopeFilter -> rule.copy(contentScope = filter.selected)
            is OrderFilter -> rule.copy(order = filter.selected)
            is PopularPeriodFilter -> rule.copy(popularPeriod = filter.selected)
            is PopularTypeFilter -> rule.copy(popularType = filter.selected)
            else -> rule
        }
    }
    return rule.normalized()
}

internal fun FilterList.saveAction(): Int = filterIsInstance<SaveActionFilter>().firstOrNull()?.state ?: 0

internal fun BlacktoonRule.summary(): String {
    val value = normalized()
    return when (value.category) {
        0 -> listOf("완결", platformLabel(value.platform), tagLabel(value.tag), contentLabel(value.contentScope), orderLabel(value.order)).joinToString(" / ")
        2 -> listOf("인기", popularPeriodLabel(value.popularPeriod), popularTypeLabel(value.popularType)).joinToString(" / ")
        else -> listOf("연재", publishingDayLabel(value.publishingDay), platformLabel(value.platform), tagLabel(value.tag), contentLabel(value.contentScope), orderLabel(value.order)).joinToString(" / ")
    }
}

private fun publishingDayLabel(value: Int) = if (value == 0) "UP" else publishingDays[value] ?: "UP"
private fun platformLabel(value: Int) = platforms[value] ?: "플랫폼 전체"
private fun tagLabel(value: Int) = tags[value] ?: "장르 전체"
private fun contentLabel(value: Int) = listOf("콘텐츠 전체", "일반 작품만", "성인 작품", "BL/백합 작품").getOrElse(value) { "콘텐츠 전체" }
private fun orderLabel(value: Int) = if (value == 1) "인기순" else "최신순"
private fun popularPeriodLabel(value: Int) = listOf("일간BEST", "주간BEST", "월간BEST", "실시간BEST").getOrElse(value) { "일간BEST" }
private fun popularTypeLabel(value: Int) = listOf("일반웹툰", "성인웹툰", "BL/백합").getOrElse(value) { "일반웹툰" }
