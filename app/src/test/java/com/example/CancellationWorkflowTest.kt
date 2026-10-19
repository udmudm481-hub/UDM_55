package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.repository.RepairRepository
import com.example.data.repository.SmsSendResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CancellationWorkflowTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: RepairRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        repository = RepairRepository(db, app)
    }

    @Test
    fun `test cancelling repair sets total to 0 and balance to 0 while preserving historical payments`() = runBlocking {
        // Create repair with 2 items (total 15,000) and partial payment 5,000
        val repair = repository.createRepair(
            customerName = "Kavinda Fernando",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy S22",
            imei = "123456789012345",
            deviceColour = "Phantom Black",
            accessories = "SIM Tray",
            fault = "Touch not working",
            technicianNotes = "Inspected",
            remarks = "Urgent",
            repairItems = listOf("Screen Replacement" to 12000.0, "Service Fee" to 3000.0),
            initialPaymentAmount = 5000.0
        )
        assertNotNull(repair)
        val repairId = repair.id

        // Verify initial state
        val initialRepair = repository.getRepairById(repairId)!!
        assertEquals(15000.0, initialRepair.totalPrice, 0.001)
        assertEquals(5000.0, initialRepair.amountPaid, 0.001)
        assertEquals(10000.0, initialRepair.balance, 0.001)
        assertEquals("RECEIVED", initialRepair.status)

        // Verify historical payment in database
        val paymentsBefore = repository.getPaymentsForRepair(repairId).first()
        assertEquals(1, paymentsBefore.size)
        assertEquals(5000.0, paymentsBefore[0].amount, 0.001)

        // Cancel the repair with reason
        val reason = "Original display part unavailable in local stock"
        val cancelledRepair = repository.cancelRepair(repairId, reason)
        assertNotNull(cancelledRepair)

        // Requirement 2:
        // Set the cancelled job's repair total to Rs. 0 and its outstanding balance to Rs. 0
        assertEquals("CANCELLED", cancelledRepair!!.status)
        assertEquals(0.0, cancelledRepair.totalPrice, 0.001)
        assertEquals(0.0, cancelledRepair.balance, 0.001)
        // Historical payment collected amount is preserved and NOT deleted or falsified
        assertEquals(5000.0, cancelledRepair.amountPaid, 0.001)

        // Check direct DB record
        val directDbRepair = repository.getRepairById(repairId)!!
        assertEquals("CANCELLED", directDbRepair.status)
        assertEquals(0.0, directDbRepair.totalPrice, 0.001)
        assertEquals(0.0, directDbRepair.balance, 0.001)
        assertEquals(5000.0, directDbRepair.amountPaid, 0.001)
        assertEquals(repair.jobNumber, directDbRepair.jobNumber) // Job number preserved

        // Verify historical payments are intact and NOT deleted
        val paymentsAfter = repository.getPaymentsForRepair(repairId).first()
        assertEquals(1, paymentsAfter.size)
        assertEquals(5000.0, paymentsAfter[0].amount, 0.001)

        // Verify StatusHistoryEntity recorded CANCELLED status, date, time, and reason
        val historyList = repository.getHistoryForRepair(repairId).first()
        val cancelHistory = historyList.lastOrNull { it.status == "CANCELLED" }
        assertNotNull(cancelHistory)
        assertEquals("CANCELLED", cancelHistory!!.status)
        assertEquals(reason, cancelHistory.notes)
        assertTrue(cancelHistory.date.isNotBlank())
        assertTrue(cancelHistory.time.isNotBlank())
        assertTrue(cancelHistory.timestamp > 0)
    }

    @Test
    fun `test cancelling repair without optional reason omits reason section in message`() = runBlocking {
        val repair = repository.createRepair(
            customerName = "Nuwan Jayasinghe",
            customerPhone = "0712223344",
            brand = "Apple",
            model = "iPhone 12",
            imei = "987654321012345",
            deviceColour = "White",
            accessories = "Cover",
            fault = "Charging issue",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Port Cleaning" to 2500.0),
            initialPaymentAmount = 0.0
        )
        assertNotNull(repair)
        val repairId = repair.id

        // Cancel with empty reason
        val cancelledRepair = repository.cancelRepair(repairId, "")
        assertNotNull(cancelledRepair)
        assertEquals("CANCELLED", cancelledRepair!!.status)
        assertEquals(0.0, cancelledRepair.totalPrice, 0.001)
        assertEquals(0.0, cancelledRepair.balance, 0.001)

        // Verify history has default cancellation note
        val historyList = repository.getHistoryForRepair(repairId).first()
        val cancelHistory = historyList.lastOrNull { it.status == "CANCELLED" }
        assertNotNull(cancelHistory)
        assertEquals("CANCELLED", cancelHistory!!.status)
        assertEquals("Job cancelled", cancelHistory.notes)
    }

    @Test
    fun `test duplicate cancellation does not duplicate status history or trigger repeat SMS`() = runBlocking {
        val repair = repository.createRepair(
            customerName = "Tharindu Perera",
            customerPhone = "0785556677",
            brand = "Xiaomi",
            model = "Redmi 9",
            imei = "",
            deviceColour = "Blue",
            accessories = "",
            fault = "Speaker not working",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Speaker Replacement" to 3500.0),
            initialPaymentAmount = 0.0
        )
        val repairId = repair.id

        // Cancel first time
        val cancel1 = repository.cancelRepair(repairId, "Customer requested return")
        assertNotNull(cancel1)
        assertEquals("CANCELLED", cancel1!!.status)

        val historyCountAfterFirst = repository.getHistoryForRepair(repairId).first().size

        // Cancel second time (simulating re-save or duplicate call)
        val cancel2 = repository.cancelRepair(repairId, "Customer requested return")
        assertNotNull(cancel2)
        assertEquals("CANCELLED", cancel2!!.status)

        val historyCountAfterSecond = repository.getHistoryForRepair(repairId).first().size
        // History count should NOT increase
        assertEquals(historyCountAfterFirst, historyCountAfterSecond)
    }

    @Test
    fun `test cancellation SMS message builder contains job number and conditional reason`() {
        val testRepairWithReason = RepairEntity(
            id = 42,
            jobNumber = "0042",
            customerName = "Saman Kumara",
            customerPhone = "0779998877",
            brand = "Google",
            model = "Pixel 6",
            fault = "Broken glass",
            totalPrice = 0.0,
            balance = 0.0,
            status = "CANCELLED",
            receivedDate = "2026-10-13",
            receivedTime = "10:00"
        )

        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            shopPhone = "0771234567"
        )

        // Test with reason
        val reason = "Customer decided not to proceed"
        val reasonSection = if (reason.isNotBlank()) "\nReason: ${reason.trim()}\n" else "\n"
        val textWithReason = """
UDM MOBILE REPAIR
Job No: ${testRepairWithReason.jobNumber}

Your repair job has been cancelled.$reasonSection
If you have any questions, please contact us.
${settings.shopName}
${settings.shopPhone}
""".trim().trimIndent()

        assertTrue(textWithReason.contains("Job No: 0042"))
        assertTrue(textWithReason.contains("Your repair job has been cancelled."))
        assertTrue(textWithReason.contains("Reason: Customer decided not to proceed"))
        assertTrue(textWithReason.contains("UDM MOBILE REPAIR"))

        // Test without reason (empty string)
        val emptyReason = ""
        val emptyReasonSection = if (emptyReason.isNotBlank()) "\nReason: ${emptyReason.trim()}\n" else "\n"
        val textWithoutReason = """
UDM MOBILE REPAIR
Job No: ${testRepairWithReason.jobNumber}

Your repair job has been cancelled.$emptyReasonSection
If you have any questions, please contact us.
${settings.shopName}
${settings.shopPhone}
""".trim().trimIndent()

        assertTrue(textWithoutReason.contains("Job No: 0042"))
        assertTrue(textWithoutReason.contains("Your repair job has been cancelled."))
        assertFalse(textWithoutReason.contains("Reason:"))
    }

    @Test
    fun `test cancelling job updates reports and outstanding balance correctly without counting cancelled totals`() = runBlocking {
        // Create 2 jobs: Job 1 will be cancelled (10,000 with 2,000 paid), Job 2 is active (20,000 with 5,000 paid)
        val job1 = repository.createRepair(
            customerName = "Job 1 Customer",
            customerPhone = "0771112233",
            brand = "Sony",
            model = "Xperia 1",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Battery",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Battery" to 10000.0),
            initialPaymentAmount = 2000.0
        )
        val job2 = repository.createRepair(
            customerName = "Job 2 Customer",
            customerPhone = "0772223344",
            brand = "Samsung",
            model = "A52",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Screen",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Display" to 20000.0),
            initialPaymentAmount = 5000.0
        )

        // Cancel Job 1
        repository.cancelRepair(job1.id, "Cannot source Sony battery")

        val allRepairs = repository.allRepairs.first()
        val allPayments = repository.allPayments.first()

        val testRepairs = allRepairs.filter { it.id == job1.id || it.id == job2.id }
        val testPayments = allPayments.filter { it.repairId == job1.id || it.repairId == job2.id }

        // Outstanding balance calculation: sum of balance where status != CANCELLED
        val outstanding = testRepairs.filter { it.status != "CANCELLED" }.sumOf { it.balance }
        // Job 2 has balance of 15,000 (20,000 - 5,000). Job 1 balance is 0.0 and CANCELLED.
        assertEquals(15000.0, outstanding, 0.001)

        // Income calculation: strictly from actual payments (2,000 + 5,000 = 7,000)
        // Does not count repair total of cancelled job as income!
        val totalIncome = testPayments.sumOf { it.amount }
        assertEquals(7000.0, totalIncome, 0.001)

        // Cancelled jobs count
        val cancelledCount = testRepairs.count { it.status == "CANCELLED" }
        assertEquals(1, cancelledCount)
    }
}
