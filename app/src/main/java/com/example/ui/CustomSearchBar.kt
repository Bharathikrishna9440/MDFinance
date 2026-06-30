package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    language: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = searchText,
        onValueChange = onSearchTextChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchText.isEmpty()) {
                        Text(
                            text = translate("Search customer by name or phone...", language),
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
                if (searchText.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchTextChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    )
}
