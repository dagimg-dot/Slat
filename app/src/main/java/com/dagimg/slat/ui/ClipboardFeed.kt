package com.dagimg.slat.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dagimg.slat.data.ClipboardEntity
import com.dagimg.slat.data.ClipboardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

const val CLIPBOARD_PAGE_SIZE = 20

/**
 * A window onto the clipboard history: [CLIPBOARD_PAGE_SIZE] rows to start with, a page more each
 * time the list runs close to its end. Room re-queries the widened window on every change, so the
 * list stays live without ever holding the whole table.
 */
@Stable
class ClipboardFeedState internal constructor(
    private val repository: ClipboardRepository,
    private val pageSize: Int,
) {
    private val limit = MutableStateFlow(pageSize)

    var items by mutableStateOf<List<ClipboardEntity>>(emptyList())
        private set

    /** True until the first query comes back, so an empty list is not mistaken for an empty history. */
    var isLoading by mutableStateOf(true)
        private set

    /**
     * Widens the window, but only once the current one has filled up — at the end of a short list
     * there is nothing further to fetch.
     */
    fun loadMore() {
        if (items.size >= limit.value) limit.value += pageSize
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun collectItems() {
        limit
            .flatMapLatest { rows -> repository.getItems(rows) }
            .collect { page ->
                items = page
                isLoading = false
            }
    }
}

@Composable
fun rememberClipboardFeed(
    repository: ClipboardRepository,
    pageSize: Int = CLIPBOARD_PAGE_SIZE,
): ClipboardFeedState {
    val state = remember(repository, pageSize) { ClipboardFeedState(repository, pageSize) }
    LaunchedEffect(state) { state.collectItems() }
    return state
}

/**
 * Asks [feed] for another page once the list is within [prefetchDistance] rows of the end, so the
 * next page is in place before the scroll reaches the bottom.
 */
@Composable
fun LoadMoreOnScrollToEnd(
    feed: ClipboardFeedState,
    listState: LazyListState,
    prefetchDistance: Int = 5,
) {
    val nearEnd by remember(feed, listState, prefetchDistance) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= feed.items.lastIndex - prefetchDistance
        }
    }
    LaunchedEffect(nearEnd, feed.items.size) {
        if (nearEnd) feed.loadMore()
    }
}
