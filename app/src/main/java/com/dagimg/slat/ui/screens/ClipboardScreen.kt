package com.dagimg.slat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dagimg.slat.ui.ClipboardFeedState
import com.dagimg.slat.ui.LoadMoreOnScrollToEnd
import com.dagimg.slat.ui.components.SearchField
import java.text.DateFormat
import java.util.Date

@Composable
fun ClipboardScreen(
    feed: ClipboardFeedState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val direction = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(direction)
    val end = contentPadding.calculateEndPadding(direction)
    val listState = rememberLazyListState()

    LoadMoreOnScrollToEnd(feed = feed, listState = listState)

    // A new query starts a new list; keeping the old offset would open it part-scrolled.
    LaunchedEffect(feed.query) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = feed.query,
            onQueryChange = feed::onQueryChange,
            modifier =
                Modifier.padding(
                    start = start,
                    end = end,
                    top = contentPadding.calculateTopPadding(),
                ),
        )

        if (feed.items.isEmpty() && !feed.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (feed.isSearching) "No clips match that search" else "Nothing copied yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = start,
                    end = end,
                    top = 12.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(feed.items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = item.getPreview(140),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestamp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
