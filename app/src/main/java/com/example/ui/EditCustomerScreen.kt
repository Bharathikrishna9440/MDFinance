package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditCustomerScreen(
    customerId: Int,
    viewModel: FinanceViewModel
) {
    val appColors = LocalAppThemeColors.current
    val customerList by viewModel.allCustomers.collectAsStateWithLifecycle()
    val collectionGroups by viewModel.collectionGroups.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val customer = customerList.find { it.id == customerId }

    if (customer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Customer record not available for editing.")
        }
        return
    }

    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phone) }
    var city by remember { mutableStateOf(customer.city ?: "") }
    var preferredLanguage by remember { mutableStateOf(customer.preferredLanguage) }
    var isNameError by remember { mutableStateOf(false) }
    var phoneErrorText by remember { mutableStateOf<String?>(null) }

    // Notification Preferences
    var smsWeeklyReminder by remember { mutableStateOf(customer.smsWeeklyReminder) }
    var smsConfirmationOfEntry by remember { mutableStateOf(customer.smsConfirmationOfEntry) }
    var autoWeeklySms by remember { mutableStateOf(customer.autoWeeklySms) }
    var autoWeeklyWhatsapp by remember { mutableStateOf(customer.autoWeeklyWhatsapp) }
    
    var collectionDay by remember { mutableStateOf(customer.collectionDay) }
    
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(translate("Edit Customer Profile", language), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorSlateDark)

            OutlinedTextField(
                value = name,
                onValueChange = { 
                    name = it
                    isNameError = false
                },
                label = { Text(translate("Customer Name", language)) },
                placeholder = { Text("E.g., Muneesh P") },
                isError = isNameError,
                supportingText = {
                    if (isNameError) {
                        Text("Borrower name is required", color = MaterialTheme.colorScheme.error)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
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
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { 
                    phone = it
                    phoneErrorText = null
                },
                label = { Text(translate("Phone Number", language)) },
                placeholder = { Text("10 digits") },
                isError = phoneErrorText != null,
                supportingText = {
                    if (phoneErrorText != null) {
                        Text(phoneErrorText!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
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
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text(translate("City", language)) },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
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
                modifier = Modifier.fillMaxWidth()
            )

            if (phone.isNotBlank()) {
                Text(translate("Preferred Language for Notifications", language), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorSlateDark)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val languages = listOf("English", "Tamil", "Hindi", "Telugu")
                    items(languages) { langItem ->
                        val isSelected = langItem == preferredLanguage
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
                                .clickable { preferredLanguage = langItem }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = translate(langItem, language),
                                color = if (isSelected) Color.White else ColorSlateDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(translate("Collection Day", language), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorSlateDark)
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(collectionGroups) { day ->
                    val isSelected = day == collectionDay
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
                            .clickable { collectionDay = day }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = day,
                            color = if (isSelected) Color.White else ColorSlateDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (phone.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(translate("SMS Confirmation", language), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorSlateDark)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { smsWeeklyReminder = !smsWeeklyReminder }
                ) {
                    Checkbox(checked = smsWeeklyReminder, onCheckedChange = { smsWeeklyReminder = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(translate("Automatic Weekly SMS Reminder", language), fontSize = 13.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { smsConfirmationOfEntry = !smsConfirmationOfEntry }
                ) {
                    Checkbox(checked = smsConfirmationOfEntry, onCheckedChange = { smsConfirmationOfEntry = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(translate("Automatic Entry Confirmation SMS", language), fontSize = 13.sp)
                }
            }
        }

        Surface(
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.navigateBack() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorLossRed),
                    border = BorderStroke(1.dp, ColorLossRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(translate("Cancel", language))
                }

                Button(
                    onClick = {
                        val cleanPhone = phone.filter { it.isDigit() }
                        isNameError = name.isBlank()
                        
                        if (phone.isNotBlank() && cleanPhone.length != 10 && cleanPhone.length != 12) {
                            phoneErrorText = "Phone must be exactly 10 or 12 digits."
                        } else {
                            phoneErrorText = null
                        }

                        if (!isNameError && phoneErrorText == null) {
                            viewModel.editCustomer(
                                customerId = customer.id,
                                name = name.trim(),
                                phone = if (phone.isBlank()) "" else cleanPhone,
                                collectionDay = collectionDay,
                                city = city.trim(),
                                smsWeeklyReminder = if (phone.isBlank()) false else smsWeeklyReminder,
                                smsConfirmationOfEntry = if (phone.isBlank()) false else smsConfirmationOfEntry,
                                autoWeeklySms = if (phone.isBlank()) false else smsWeeklyReminder,
                                autoWeeklyWhatsapp = autoWeeklyWhatsapp,
                                upiNameAlias = "",
                                preferredLanguage = preferredLanguage
                            )
                            viewModel.navigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primaryAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(translate("Save", language), color = Color.White)
                }
            }
        }
    }
}
