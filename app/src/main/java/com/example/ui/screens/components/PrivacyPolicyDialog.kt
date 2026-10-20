package com.example.ui.screens.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyPolicyDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {}, // Cannot dismiss without accepting
        title = { Text("Privacy & Data Policy", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "To provide you with the best experience, this app handles your data as follows:\n\n" +
                        "1. Gemini API Key: Your API key is encrypted and stored locally in your device's secure storage (Android KeyStore/EncryptedSharedPreferences). It is never logged or shared.\n\n" +
                        "2. Audio Data: Audio captured during Live conversations is processed by the Gemini Live API for real-time interaction. It is not stored by this app.\n\n" +
                        "By proceeding, you acknowledge these practices.",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("I Accept", fontWeight = FontWeight.Bold)
            }
        }
    )
}
