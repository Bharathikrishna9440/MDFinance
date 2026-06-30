package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WednesdayScreen(viewModel: FinanceViewModel) {
    val activeDay = "Wednesday"
    val appColors = LocalAppThemeColors.current
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val overviewList by viewModel.customerOverviewList.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.selectDay("Wednesday")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(translate("Wednesday Collections", language), fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorSlateDark)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                StatsReportingCard(stats = stats, selectedDay = activeDay, language = language)
            }

            // Search Bar
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    CustomSearchBar(
                        searchText = searchText,
                        onSearchTextChange = { viewModel.updateSearchText(it) },
                        language = language,
                        modifier = Modifier
                            .weight(1.0f)
                            .height(50.dp)
                    )
                }
            }

            if (overviewList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Empty",
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = translate("No customers in Wednesday's list yet.", language),
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    count = overviewList.size,
                    key = { index -> overviewList[index].customer.id }
                ) { index ->
                    val item = overviewList[index]
                    CustomerOverviewCard(
                        item = item,
                        displayIndex = item.originalGroupIndex,
                        viewModel = viewModel,
                        language = language,
                        activeDay = activeDay,
                        fontSizeScale = fontSizeScale,
                        showReorder = false,
                        onCardClicked = { viewModel.navigateTo(Screen.CustomerDetail(item.customer.id)) },
                        onMoveUp = {},
                        onMoveDown = {},
                        onReceiveClicked = { viewModel.navigateTo(Screen.RecordPayment(it)) },
                        onAddLoanClicked = { viewModel.navigateTo(Screen.AddLoan(item.customer.id)) },
                        onIndexClicked = {}
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}
