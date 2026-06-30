package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutomationSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    smsPaused: Boolean,
    smsReaderPaused: Boolean,
    autoEntryPassing: Boolean,
    upiLinkSharing: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Outbound SMS Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Outbound SMS Sending", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (smsPaused) ColorLossRed else ColorSlateDark
                    )
                    Text(
                        text = translate("Pause or enable outbound confirmation and draft SMS.", language),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                Switch(
                    checked = !smsPaused,
                    onCheckedChange = { viewModel.setSmsPaused(!it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = ColorSlateDark, checkedTrackColor = ColorSlateDark.copy(alpha = 0.4f))
                )
            }
        }
    }
}
