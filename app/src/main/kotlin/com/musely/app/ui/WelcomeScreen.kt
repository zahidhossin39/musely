package com.musely.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musely.app.R
import com.musely.app.ui.theme.LocalSpotlight

/**
 * First-run screen: introduces Musely and asks for access to the device's audio files.
 * [declined] switches the copy to a gentle retry with a path to system settings.
 */
@Composable
fun WelcomeScreen(declined: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    val logoShape = RoundedCornerShape(36.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.primaryContainer.copy(alpha = 0.55f), 0.6f to Color.Transparent))
            .safeDrawingPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(R.drawable.musely_logo),
            contentDescription = "Musely logo",
            modifier = Modifier
                .size(124.dp)
                .shadow(28.dp, logoShape, ambientColor = colors.primary, spotColor = colors.primary)
                .clip(logoShape),
        )
        Spacer(Modifier.height(36.dp))
        Text(
            "MUSELY",
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text("Hear your music like it's the first time.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            if (declined) {
                "Musely can't play what it can't see. Allow access to your audio files and your library appears in seconds."
            } else {
                "Musely turns the songs already on your phone into a calm, beautiful library. No accounts. No ads. Just you and your music."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = spotlight.container, contentColor = spotlight.content),
        ) {
            Text(if (declined) "Try again" else "Let the music in", style = MaterialTheme.typography.titleMedium)
        }
        if (declined) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Open app settings")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Your library never leaves this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}
