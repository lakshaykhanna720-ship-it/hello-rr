package com.driverledger.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import com.driverledger.app.data.AppDatabase
import com.driverledger.app.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

class PaymentNotificationListener : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private lateinit var repo: TransactionRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
            }
        }

        repo = TransactionRepository(
            AppDatabase.getInstance(applicationContext).transactionDao()
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val packageName = sbn.packageName.lowercase()

        // Only listen to the four payment apps.
        if (!isSupportedPaymentApp(packageName)) return

        val extras = sbn.notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        val text = listOf(
            title,
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        ).joinToString(" ")

        if (!looksLikeIncomingPayment(text)) return

        val amount = extractAmount(text) ?: return

        val source = when {
            packageName.contains("paytm") -> "Paytm"
            packageName.contains("phonepe") -> "PhonePe"
            packageName.contains("famapp") -> "FamApp"
            isGooglePay(packageName) -> "GPay"
            else -> return
        }

        scope.launch {

            /*
             * Your existing repository already prevents the same
             * amount/source from being inserted twice within 5 seconds.
             *
             * This is useful because payment apps can update/post
             * the same notification more than once.
             */
            val wasNew = repo.addAutoDetectedPaymentIfNew(
                amount = amount,
                source = source
            )

            if (wasNew) {
                withContext(Dispatchers.Main) {
                    tts?.speak(
                        "Payment received. ${amount.toSpeechAmount()} rupees.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "payment"
                    )
                }
            }
        }
    }

    private fun isSupportedPaymentApp(packageName: String): Boolean {
        return packageName.contains("paytm") ||
                packageName.contains("phonepe") ||
                packageName.contains("famapp") ||
                isGooglePay(packageName)
    }

    private fun isGooglePay(packageName: String): Boolean {
        return packageName.contains("googlequicksearchbox") ||
                packageName.contains("googlepay") ||
                packageName.contains("gpay")
    }

    private fun looksLikeIncomingPayment(text: String): Boolean {

        val s = text.lowercase().trim()

        // Ignore obvious outgoing payments.
        if (
            Regex(
                """\b(?:you|i)\s+(?:sent|paid)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(s)
        ) {
            return false
        }

        /*
         * Paytm / FamApp style:
         *
         * "Amit Khanna sent ₹100"
         * "Rajni Kapoor sent 1 rupees"
         */
        val personSentPayment = Regex(
            """.+?\bsent\s+(?:₹|rs\.?|inr)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\b""",
            RegexOption.IGNORE_CASE
        )

        if (personSentPayment.containsMatchIn(s)) {
            return true
        }

        /*
         * Google Pay style:
         *
         * "Lakshay Khanna paid you 1 rupees"
         * "Rahul paid you ₹100"
         */
        val personPaidYou = Regex(
            """.+?\bpaid\s+you\s+(?:₹|rs\.?|inr)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\b""",
            RegexOption.IGNORE_CASE
        )

        if (personPaidYou.containsMatchIn(s)) {
            return true
        }

        /*
         * Other incoming wording used by payment apps.
         */
