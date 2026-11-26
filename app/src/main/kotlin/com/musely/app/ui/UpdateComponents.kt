package com.musely.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.musely.app.R
import com.musely.app.UpdateStatus
import com.musely.app.ui.theme.LocalSpotlight

/** Headline for the update card, one per [UpdateStatus]. */
internal fun updateTitle(status: UpdateStatus): String = when (status) {
    is UpdateStatus.Available -> "Update available"
    is UpdateStatus.UpToDate -> "You're up to date"
    UpdateStatus.Checking -> "Checking for updates"
    is UpdateStatus.Error -> "Update check failed"
    UpdateStatus.Idle -> "App updates"
}

/** Settings card showing the installed version, the latest GitHub release and update actions. */
@Composable
internal fun UpdateCard(
    installedVersion: String,
    status: UpdateStatus,
    onCheckAgain: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_update),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = spotlight.container,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(updateTitle(status), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Installed: v$installedVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            UpdateStatusBody(status = status, onCheckAgain = onCheckAgain, onOpenUrl = onOpenUrl)
        }
    }
}

@Composable
private fun UpdateStatusBody(
    status: UpdateStatus,
    onCheckAgain: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    when (status) {
        UpdateStatus.Checking -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = spotlight.container,
                )
                Text(
                    "Checking GitHub Releases…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        is UpdateStatus.Available -> {
            val info = status.info
            Surface(
                color = colors.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Musely v${info.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = spotlight.container,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = info.releaseNotes.ifBlank { "A new version of Musely is ready to download." },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(label = "Update", onClick = { onOpenUrl(info.apkUrl) })
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (info.releaseUrl.isNotBlank()) {
                    TextButton(onClick = { onOpenUrl(info.releaseUrl) }) { Text("Release notes") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton(onClick = onCheckAgain) { Text("Check again") }
            }
        }

        is UpdateStatus.UpToDate -> {
            Text(
                "Musely v${status.currentVersion} is the latest release.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
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
                color = colors.error,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(label = "Check again", onClick = onCheckAgain)
        }

        UpdateStatus.Idle -> PrimaryActionButton(label = "Check for updates", onClick = onCheckAgain)
    }
}

@Composable
private fun PrimaryActionButton(label: String, onClick: () -> Unit) {
    val spotlight = LocalSpotlight.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = spotlight.container,
            contentColor = spotlight.content,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
