package com.musely.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.musely.app.R
import com.musely.app.UpdateStatus
import com.musely.app.ui.theme.LocalSpotlight

/**
 * Compact icon button for the top-right corner of the BrandBar row.
 * Displays an accent dot badge when [hasUpdate] is true.
 */
@Composable
fun UpdateTopBarButton(
    hasUpdate: Boolean,
    checking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spotlight = LocalSpotlight.current
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = spotlight.container,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_update),
                    contentDescription = if (hasUpdate) "Update available" else "Check for updates",
                    modifier = Modifier.size(22.dp),
                    tint = if (hasUpdate) spotlight.container else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(spotlight.container)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
        }
    }
}

/** Material 3 bottom sheet displaying the current app version and update status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    installedVersion: String,
    status: UpdateStatus,
    onDismiss: () -> Unit,
    onCheckAgain: () -> Unit,
    onUpdateClick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val spotlight = LocalSpotlight.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_update),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = spotlight.container,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val title = when (status) {
                        is UpdateStatus.Available -> "Update available"
                        is UpdateStatus.UpToDate -> "You're up to date"
                        is UpdateStatus.Checking -> "Checking for updates"
                        is UpdateStatus.Error -> "Update check failed"
                        UpdateStatus.Idle -> "App updates"
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Installed: v$installedVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when (status) {
                UpdateStatus.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = spotlight.container,
                        )
                        Text(
                            "Checking GitHub Releases…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is UpdateStatus.Available -> {
                    val info = status.info
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Musely v${info.version}",
                                style = MaterialTheme.typography.titleMedium,
                                color = spotlight.container,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = info.releaseNotes.ifBlank {
                                    "A new version of Musely is ready to download."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { onUpdateClick(info.apkUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spotlight.container,
                            contentColor = spotlight.content,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Update")
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (info.releaseUrl.isNotBlank()) {
                            TextButton(onClick = { onUpdateClick(info.releaseUrl) }) {
                                Text("Release notes")
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        TextButton(onClick = onCheckAgain) {
                            Text("Check again")
                        }
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Text(
                        "Musely v${status.currentVersion} is the latest release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onCheckAgain,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Check again")
                    }
                }

                is UpdateStatus.Error -> {
                    Text(
                        status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onCheckAgain,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spotlight.container,
                            contentColor = spotlight.content,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Check again")
                    }
                }

                UpdateStatus.Idle -> {
                    Button(
                        onClick = onCheckAgain,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spotlight.container,
                            contentColor = spotlight.content,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Check again")
                    }
                }
            }
        }
    }
}
