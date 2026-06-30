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
fun SIMSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    simSelection: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Select which SIM Card is default on your physical Android slot for delivery reminders.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val slots = listOf(
                    "Ask Always" to "Default Slot (Ask Always)",
                    "SIM 1" to "SIM card Slot 1",
                    "SIM 2" to "SIM card Slot 2"
                )

                slots.forEach { (slotVal, label) ->
                    val isSel = simSelection == slotVal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSel) ColorSlateDark.copy(alpha = 0.05f) else Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) ColorSlateDark else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = translate(label, language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ColorSlateDark
                            )
                        }

                        RadioButton(
                            selected = isSel,
                            onClick = { viewModel.setSimSelection(slotVal) },
                            colors = RadioButtonDefaults.colors(selectedColor = ColorSlateDark)
                        )
                    }
                }
            }
        }
    }
}
