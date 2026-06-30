package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CollectionGroupsSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    collectionGroups: List<String>,
    customGroupName: String,
    onCustomGroupNameChange: (String) -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Configure active route days or groups for collection cycles.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            // Add Group Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customGroupName,
                    onValueChange = onCustomGroupNameChange,
                    placeholder = { Text("Add Group Name e.g. Tuesday / செவ்வாய்", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        val trimmed = customGroupName.trim()
                        if (trimmed.isNotBlank()) {
                            if (collectionGroups.contains(trimmed)) {
                                // Already contains
                            } else {
                                val newList = collectionGroups.toMutableList().apply { add(trimmed) }
                                viewModel.updateCollectionGroups(newList)
                                onCustomGroupNameChange("")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Reorder and list area
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                collectionGroups.forEachIndexed { index, group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorSlateDark,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Move Up
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = collectionGroups.toMutableList().apply {
                                            val temp = get(index)
                                            set(index, get(index - 1))
                                            set(index - 1, temp)
                                        }
                                        viewModel.updateCollectionGroups(newList)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) ColorSlateDark else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Move Down
                            IconButton(
                                onClick = {
                                    if (index < collectionGroups.size - 1) {
                                        val newList = collectionGroups.toMutableList().apply {
                                            val temp = get(index)
                                            set(index, get(index + 1))
                                            set(index + 1, temp)
                                        }
                                        viewModel.updateCollectionGroups(newList)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (index < collectionGroups.size - 1) ColorSlateDark else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Rename
                            IconButton(
                                onClick = { onRenameClick(group) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename group",
                                    tint = ColorSlateDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Delete
                            IconButton(
                                onClick = { onDeleteClick(group) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete group",
                                    tint = ColorLossRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
