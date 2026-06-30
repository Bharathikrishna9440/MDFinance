package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
fun FontSizeSubPage(
    language: String,
    fontSizeScale: Float,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = translate("Adjust the size of the application's text elements to suit your visual comfort.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            val fontSizeOptions = listOf(
                Triple("Normal", 1.0f, "100%"),
                Triple("Medium", 1.15f, "115%"),
                Triple("Large", 1.30f, "130%"),
                Triple("Extra Large", 1.45f, "145%")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fontSizeOptions.forEach { (label, scale, percent) ->
                    val isSel = fontSizeScale == scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isSel) appColors.primaryAccent else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) appColors.primaryAccent else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setFontSizeScale(scale) }
                            .padding(vertical = 12.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = translate(label, language),
                                color = if (isSel) Color.White else appColors.primaryAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = percent,
                                color = if (isSel) Color.LightGray else Color.Gray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
