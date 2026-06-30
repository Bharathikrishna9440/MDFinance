package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep Slate
            Color(0xFF1E1F38), // Midnight Indigo
            Color(0xFF131424)  // Dark Cosmic Void
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual Logo / Icon Frame
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF2563EB).copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .border(2.dp, Color(0xFF2563EB).copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Icon",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App Brand Headings
            Text(
                text = "MD FINANCE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Secured Ledger Management System",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.LightGray.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error Display Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Username Input Field
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("Username", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Icon",
                        tint = Color.Gray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                    cursorColor = Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Password Input Field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Icon",
                        tint = Color.Gray
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color.Gray
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isBlank()) {
                            errorMessage = "Please enter your username."
                        } else if (password.isBlank()) {
                            errorMessage = "Please enter your password."
                        } else {
                            isLoading = true
                            viewModel.login(username, password, context, {
                                isLoading = false
                            }, { err ->
                                isLoading = false
                                errorMessage = err
                            })
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                    cursorColor = Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Login Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (username.isBlank()) {
                        errorMessage = "Please enter your username."
                        return@Button
                    }
                    if (password.isBlank()) {
                        errorMessage = "Please enter your password."
                        return@Button
                    }
                    isLoading = true
                    viewModel.login(username, password, context, {
                        isLoading = false
                    }, { err ->
                        isLoading = false
                        errorMessage = err
                    })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_button"),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Login",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Security Note Badge
            Text(
                text = "Licensed purely for MD Finance business administration. Unauthorized actions are audited.",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}
