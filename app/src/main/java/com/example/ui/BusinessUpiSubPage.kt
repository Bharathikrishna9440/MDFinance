package com.example.ui

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BusinessUpiSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    businessName: String,
    upiId: String,
    upiLink: String,
    qrImageUri: String,
    qrPickerLauncher: ActivityResultLauncher<String>
) {
    val statementCustomizationCode by viewModel.statementCustomizationCode.collectAsStateWithLifecycle()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Customize billing identities & business notification footer settings.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            // Business Name
            OutlinedTextField(
                value = businessName,
                onValueChange = { viewModel.setBusinessName(it) },
                label = { Text("Business Name Signal Title (SMS footer)", fontSize = 11.sp) },
                placeholder = { Text("Example: Muneeswaran Finance", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            // Merchant UPI ID
            OutlinedTextField(
                value = upiId,
                onValueChange = { viewModel.setUpiId(it) },
                label = { Text("Merchant UPI ID (for QR codes and SMS formats)", fontSize = 11.sp) },
                placeholder = { Text("Example: 9440736893@ptyes", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

            Text(
                text = "Statement Customization Override",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ColorSlateDark
            )
            Text(
                text = "Override dynamic components during ledger image sharing. Enter standard key-value configuration overrides below (e.g. TITLE, FOOTER, WATERMARK, WATERMARK_SUB, COLOR_START, COLOR_END, THEME_BORDER_COLOR).",
                fontSize = 11.sp,
                color = Color.Gray
            )

            OutlinedTextField(
                value = statementCustomizationCode,
                onValueChange = { viewModel.setStatementCustomizationCode(it) },
                label = { Text("Customization Configuration Code", fontSize = 11.sp) },
                placeholder = { Text("TITLE=COLLECTION STATEMENT REPORT\nFOOTER=Please retain this statement for your verification.\nCOLOR_START=#0F172A\nCOLOR_END=#1E1B4B\nTHEME_BORDER_COLOR=#4F46E5", fontSize = 11.sp) },
                minLines = 4,
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedBorderColor = LocalAppThemeColors.current.darkBg,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
