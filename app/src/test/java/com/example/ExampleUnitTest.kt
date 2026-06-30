package com.example

import com.example.data.Customer
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testViewModelInit() {
    val application = RuntimeEnvironment.getApplication()
    val viewModel = com.example.ui.FinanceViewModel(application)
    assertNotNull(viewModel)
  }
}

