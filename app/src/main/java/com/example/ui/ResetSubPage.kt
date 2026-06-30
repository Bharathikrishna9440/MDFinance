package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResetSubPage(
    language: String,
    onResetTemplatesClick: () -> Unit,
    onResetAllClick: () -> Unit,
    appColors: AppThemeColors
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Perform system maintenance actions. You can reset configurable variables and notification templates back to original system defaults, or clear all entries to start fresh.", language),
                fontSize = 11.sp,
                color = Color.Black,
                lineHeight = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResetTemplatesClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = ColorSlateDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(translate("Reset Templates?", language).substringBefore("?"), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }

                Button(
                    onClick = onResetAllClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(translate("Clear All Data", language), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}
