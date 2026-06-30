package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TemplatesSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    smsPaused: Boolean,
    currentTemplateLang: String,
    smsNewLoanTemplate: String,
    smsPaymentTemplate: String,
    smsReminderTemplate: String,
    whatsappReminderTemplate: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Outbound SMS Controls Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ColorSlateDark,
                        checkedTrackColor = ColorSlateDark.copy(alpha = 0.4f),
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.LightGray.copy(alpha = 0.4f)
                    )
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Text(
                 text = translate("Configure custom templates for SMS/WhatsApp confirmations and alerts. Use the dynamic wildcards listed below. They will be autoreplaced when sending:", language),
                 fontSize = 11.sp,
                 color = Color.DarkGray
            )

            // List variables
            val variables = listOf(
                "{customer}" to "Client's registration name ({name} is also supported)",
                "{amount}" to "Active payment / transaction amount",
                "{business}" to "My custom businesssignature tag",
                "{upi}" to "Configured business merchant UPI ID",
                "{upi_link}" to "Direct UPI pay link (QR Link / URI)",
                "{balance}" to "Remaining outstanding balance",
                "{inst_amt}" to "Standard collection installment amount",
                "{date}" to "Current formatted entry timestamp"
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                variables.forEach { (token, label) ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = translate(label, language),
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            text = token,
                            color = appColors.primaryAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Text(
                text = translate("Select Language for Custom Templates:", language),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ColorSlateDark
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                val languages = listOf("English", "Tamil", "Hindi", "Telugu")
                languages.forEach { langItem ->
                    val isSelected = langItem == currentTemplateLang
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) ColorSlateDark else Color.White,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ColorSlateDark else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.setTemplateLanguage(langItem) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = translate(langItem, language),
                            color = if (isSelected) Color.White else ColorSlateDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // New Loan Custom Area
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("1. New Loan Confirmation SMS Template", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorSlateDark)
                OutlinedTextField(
                    value = smsNewLoanTemplate,
                    onValueChange = { viewModel.setSmsNewLoanTemplate(it) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            // Payment Confirmation Custom Area
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("2. Entry Confirmation SMS Template", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorSlateDark)
                OutlinedTextField(
                    value = smsPaymentTemplate,
                    onValueChange = { viewModel.setSmsPaymentTemplate(it) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            // SMS Reminder Custom Area
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("3. Weekly SMS Reminder Template", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorSlateDark)
                OutlinedTextField(
                    value = smsReminderTemplate,
                    onValueChange = { viewModel.setSmsReminderTemplate(it) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.White, // Wait, color of focused label can be black or primary
                        unfocusedLabelColor = Color.DarkGray,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            // Whatsapp Reminder Custom Area
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("4. Weekly WhatsApp Reminder Template", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorSlateDark)
                OutlinedTextField(
                    value = whatsappReminderTemplate,
                    onValueChange = { viewModel.setWhatsappReminderTemplate(it) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        }
    }
}
