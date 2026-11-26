package com.musely.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musely.app.R

/** The Downloads tab. */
@Composable
internal fun DownloadsScreen(listState: LazyListState, bottomInset: Dp) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 24.dp),
    ) {
        item(key = "header") {
            TabHeader(title = "Downloads", subtitle = "Save music and videos for offline listening.")
        }
        item(key = "empty") {
            StateMessage(
                title = "Nothing downloaded yet",
                body = "Downloaded songs and videos will appear here.",
                iconRes = R.drawable.ic_download,
            )
        }
    }
}
