package com.mar.gym.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MediaFullscreenDialog(url: String, onDismiss: () -> Unit, tag: String) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag(tag)) {
            SocialMediaImage(url, "Imagen ampliada", Modifier.fillMaxSize(), ContentScale.Fit)
            TextButton(onClick = onDismiss, Modifier.align(Alignment.TopStart)) { Text("Volver") }
        }
    }
}
