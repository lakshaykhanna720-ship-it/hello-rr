@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.driverledger.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var tts: TextToSpeech
    private val viewModel: LedgerViewModel by viewModels()

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale("en", "IN")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                val todayTotal by viewModel.todayIncomeTotal.collectAsState()
                val todayCount by viewModel.todayPaymentCount.collectAsState()
                val weekIncome by viewModel.weekIncomeTotal.collectAsState()
                val weekExpense by viewModel.weekExpenseTotal.collectAsState()

                DriverLedgerScreen(
                    todayTotal = todayTotal,
                    todayCount = todayCount,
                    weekIncome = weekIncome,
                    weekExpense = weekExpense,
                    onEnableDetection = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onSpeakTest = {
                        tts.speak(
                            "This is a test announcement.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "test"
                        )
                    },
                    onAddPayment = { viewModel.addPayment(it) },
                    onAddExpense = { viewModel.addExpense(it) }
                )
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun DriverLedgerScreen(
    todayTotal: Double,
    todayCount: Int,
    weekIncome: Double,
    weekExpense: Double,
    onEnableDetection: () -> Unit,
    onSpeakTest: () -> Unit,
    onAddPayment: (Double) -> Unit,
    onAddExpense: (Double) -> Unit
) {
    var driving by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }

    val weekNet = weekIncome - weekExpense

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚕 Driver Ledger") }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "TODAY'S EARNINGS",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            formatRupees(todayTotal),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            "$todayCount payment${if (todayCount == 1) "" else "s"}"
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { driving = !driving },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text(
                        if (driving) "🟢 DRIVING MODE ON"
                        else "🚕 START DRIVING MODE"
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showAdd = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ PAYMENT")
                    }

                    OutlinedButton(
                        onClick = { showExpense = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("− EXPENSE")
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "THIS WEEK",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Gross earnings       ${formatRupees(weekIncome)}")
                        Text("Expenses              − ${formatRupees(weekExpense)}")
                        HorizontalDivider(
                            Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            "NET EARNINGS          ${formatRupees(weekNet)}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            item {
                Text(
                    "QUICK ACTIONS",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedButton(
                    onClick = onEnableDetection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📲 Enable payment notification detection")
                }

                OutlinedButton(
                    onClick = onSpeakTest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔊 Test voice announcement")
                }
            }
        }
    }

    if (showAdd) {
        SimpleAmountDialog(
            "Add payment",
            "Payment amount"
        ) { amount ->
            amount?.let(onAddPayment)
            showAdd = false
        }
    }

    if (showExpense) {
        SimpleAmountDialog(
            "Add expense",
            "Expense amount"
        ) { amount ->
            amount?.let(onAddExpense)
            showExpense = false
        }
    }
}

private fun formatRupees(amount: Double): String {
    val rounded = Math.round(amount)
    return "₹" + "%,d".format(rounded)
}

@Composable
private fun SimpleAmountDialog(
    title: String,
    label: String,
    onClose: (Double?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = { onClose(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(label) },
                isError = amount.isNotEmpty() && parsed == null
            )
        },
        confirmButton = {
            Button(
                onClick = { onClose(parsed) },
                enabled = parsed != null && parsed > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onClose(null) }
            ) {
                Text("Cancel")
            }
        }
    )
}
