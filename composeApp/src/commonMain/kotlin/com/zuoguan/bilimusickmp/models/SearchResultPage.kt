package com.zuoguan.bilimusickmp.models

/**
 * 搜索一页的结果。
 *
 * [hasMore] 由各平台接口返回的总数/总页数算出，而不是等下一页返回空列表才知道到底：
 * 搜索结果本来就少时，第一页就能确定没有下一页，界面不必再发一次必然为空的请求
 * （B 站翻过最后一页时不返回 result 字段，多发的请求会直接报错）。
 *
 * @property items 本页结果。
 * @property hasMore 接口是否还有下一页。
 */
data class SearchResultPage(
    val items: List<SearchResult>,
    val hasMore: Boolean
)
