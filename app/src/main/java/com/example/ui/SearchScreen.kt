package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Customer
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    day: String,
    viewModel: FinanceViewModel
) {
    val appColors = LocalAppThemeColors.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val activeLoanCycles by viewModel.activeLoanCycles.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Request focus on entry
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Filter and map customers specifically for the chosen collection day
    val filteredOverviewList = remember(day, searchQuery, allCustomers, activeLoanCycles, allLoanCycles, allPayments) {
        val baseList = getOverviewListForDay(
            day = day,
            allCustomers = allCustomers,
            activeLoanCycles = activeLoanCycles,
            allLoanCycles = allLoanCycles,
            allPayments = allPayments,
            search = searchQuery,
            sortMode = "ROUTE" // keep route order / custom order sort
        )
        baseList
    }

    val displayDayName = if (day.equals("Home", ignoreCase = true)) {
        translate("All Customers", language)
    } else {
        translate(day, language)
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF0F172A), // SLEEK Black-Slate bar as in user screenshot
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("search_page_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Day view",
                            tint = Color.White
                        )
                    }

                    // Input Box row
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "${translate("Search", language)} ($displayDayName)...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontSize = 16.sp
                            ),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = appColors.primaryAccent
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag("search_page_input")
                        )

                        // Clear query trigger
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search query",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Black // Match fully sleek dark background from custom user screens
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            if (filteredOverviewList.isEmpty()) {
                // High fidelity centered label matching exactly the uploaded screen shot: "No items"
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = translate("No items", language),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("search_no_items_text")
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filteredOverviewList,
                        key = { _, item -> item.customer.id }
                    ) { index, item ->
                        CustomerOverviewCard(
                            item = item,
                            displayIndex = item.originalGroupIndex,
                            viewModel = viewModel,
                            language = language,
                            activeDay = day,
                            fontSizeScale = fontSizeScale,
                            showReorder = false,
                            onCardClicked = { viewModel.navigateTo(Screen.CustomerDetail(item.customer.id)) },
                            onMoveUp = {},
                            onMoveDown = {},
                            onReceiveClicked = { viewModel.navigateTo(Screen.RecordPayment(it)) },
                            onAddLoanClicked = { viewModel.navigateTo(Screen.AddLoan(item.customer.id)) },
                            onIndexClicked = {},
                            isScrolling = false,
                            onEditPaymentClicked = { activeLoanId ->
                                // Safely handle payment edit redirection directly to Customer details screen for full operations
                                viewModel.navigateTo(Screen.CustomerDetail(item.customer.id))
                            }
                        )
                    }
                }
            }
        }
    }
}
