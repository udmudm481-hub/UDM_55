package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.NewRepairScreen
import com.example.ui.theme.UDMRepairTheme
import com.example.ui.viewmodel.RepairViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UDM Mobile Repair", appName)
    }

    @Test
    fun `test 4-digit job number formatting`() {
        val num = 14
        val formatted = String.format(Locale.US, "%04d", num)
        assertEquals("0014", formatted)

        val singleDigit = 1
        assertEquals("0001", String.format(Locale.US, "%04d", singleDigit))

        val fourDigit = 1250
        assertEquals("1250", String.format(Locale.US, "%04d", fourDigit))
    }

    @Test
    fun `test job number normalization from OCR`() {
        val rawOcr1 = "JOB NO: 0014"
        val digits1 = rawOcr1.replace(Regex("[^0-9]"), "")
        val normalized1 = String.format(Locale.US, "%04d", digits1.toInt())
        assertEquals("0014", normalized1)

        val rawOcr2 = "14"
        val digits2 = rawOcr2.replace(Regex("[^0-9]"), "")
        val normalized2 = String.format(Locale.US, "%04d", digits2.toInt())
        assertEquals("0014", normalized2)
    }

    @Test
    fun `test NewRepairScreen renders without layout crash and input fields accept text`() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = RepairViewModel(application)

        composeTestRule.setContent {
            UDMRepairTheme {
                NewRepairScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToCustomerHistory = {},
                    onRepairCreated = {}
                )
            }
        }

        // Verify key inputs and actions are displayed
        composeTestRule.onNodeWithTag("input_customer_phone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_customer_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_repair_button").assertIsDisplayed()

        // Test typing into input fields (simulating keyboard interaction)
        composeTestRule.onNodeWithTag("input_customer_phone").performTextInput("0771234567")
        composeTestRule.onNodeWithTag("input_customer_name").performTextInput("John Doe")
    }

    @Test
    fun `test yesterday income calculation and currency formatting`() {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val now = java.util.Date()
        val todayStr = dateFormat.format(now)

        val cal = java.util.Calendar.getInstance().apply {
            time = now
            add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayStr = dateFormat.format(cal.time)

        // Simulate payment transactions
        val payments = listOf(
            // Yesterday initial payment
            com.example.data.entity.PaymentEntity(id = 1, repairId = 101, paymentNumber = 1, amount = 1500.0, date = yesterdayStr, time = "10:30"),
            // Yesterday subsequent partial payment
            com.example.data.entity.PaymentEntity(id = 2, repairId = 102, paymentNumber = 2, amount = 2500.0, date = yesterdayStr, time = "16:45"),
            // Today payment
            com.example.data.entity.PaymentEntity(id = 3, repairId = 103, paymentNumber = 1, amount = 4000.0, date = todayStr, time = "09:15"),
            // Older payment
            com.example.data.entity.PaymentEntity(id = 4, repairId = 104, paymentNumber = 1, amount = 8000.0, date = "2026-01-01", time = "12:00")
        )

        val todayIncome = payments
            .filter { p ->
                p.amount > 0 && (
                    p.date.trim() == todayStr ||
                    p.date.trim().startsWith(todayStr)
                )
            }
            .sumOf { it.amount }

        val yesterdayIncome = payments
            .filter { p ->
                p.amount > 0 &&
                !p.date.trim().startsWith(todayStr) &&
                (
                    p.date.trim() == yesterdayStr ||
                    p.date.trim().startsWith(yesterdayStr)
                )
            }
            .sumOf { it.amount }

        assertEquals(4000.0, todayIncome, 0.001)
        assertEquals(4000.0, yesterdayIncome, 0.001) // 1500 + 2500

        // Test LKR formatting matching Today's Income
        val currency = "LKR"
        val formattedYesterday = "$currency ${String.format(java.util.Locale.US, "%,.0f", yesterdayIncome)}"
        val formattedToday = "$currency ${String.format(java.util.Locale.US, "%,.0f", todayIncome)}"
        assertEquals("LKR 4,000", formattedYesterday)
        assertEquals("LKR 4,000", formattedToday)

        // Test zero payments yesterday returns 0.0
        val emptyPayments = emptyList<com.example.data.entity.PaymentEntity>()
        val zeroYesterdayIncome = emptyPayments
            .filter { it.date == yesterdayStr }
            .sumOf { it.amount }
        assertEquals(0.0, zeroYesterdayIncome, 0.001)
        val formattedZero = "$currency ${String.format(java.util.Locale.US, "%,.0f", zeroYesterdayIncome)}"
        assertEquals("LKR 0", formattedZero)
    }

    @Test
    fun `test zero price validation logic accepts 0 and rejects blank, non-numeric, or negative`() {
        fun isValidPrice(priceStr: String): Boolean {
            val p = priceStr.toDoubleOrNull()
            return priceStr.isNotBlank() && p != null && p >= 0.0
        }

        // Explicit 0 is valid
        org.junit.Assert.assertTrue(isValidPrice("0"))
        org.junit.Assert.assertTrue(isValidPrice("0.0"))
        org.junit.Assert.assertTrue(isValidPrice("1500"))

        // Blank, non-numeric, negative are rejected
        org.junit.Assert.assertFalse(isValidPrice(""))
        org.junit.Assert.assertFalse(isValidPrice("   "))
        org.junit.Assert.assertFalse(isValidPrice("abc"))
        org.junit.Assert.assertFalse(isValidPrice("-1"))
        org.junit.Assert.assertFalse(isValidPrice("-0.01"))
    }

    @Test
    fun `test repository saves zero price repair job correctly`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val created = repository.createRepair(
            customerName = "Chamara Silva",
            customerPhone = "0719876543",
            brand = "Apple",
            model = "iPhone 13",
            imei = "998877665544332",
            deviceColour = "Blue",
            accessories = "None",
            fault = "Check Only",
            technicianNotes = "Warranty inspection",
            remarks = "Free check",
            repairItems = listOf("Diagnostic Check" to 0.0),
            initialPaymentAmount = 0.0
        )

        assertNotNull(created)
        assertEquals(0.0, created.totalPrice, 0.001)
        assertEquals(0.0, created.balance, 0.001)
        assertEquals("UNPAID", created.paymentStatus)
        assertEquals("RECEIVED", created.status)

        // Verify items in DB
        val items = db.repairItemDao().getItemsForRepairDirect(created.id)
        assertEquals(1, items.size)
        assertEquals(0.0, items[0].price, 0.001)
        assertEquals("Diagnostic Check", items[0].repairType)
    }

    @Test
    fun `test suggestions combine exact brand model prices with master repair types and exclude other models`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        // Setup prices for different devices
        repository.savePrice("Samsung", "Galaxy S21", "OLED Screen", 25000.0)
        repository.savePrice("Samsung", "Galaxy S21", "Battery Replacement", 6500.0)
        repository.savePrice("Apple", "iPhone 14", "iPhone Screen", 45000.0)

        // Custom repair types
        repository.addRepairTypeIfNew("Charging Port")
        repository.addRepairTypeIfNew("Microphone")

        val allPrices = db.priceDao().getAllPricesDirect()
        val allRepairTypes = db.repairTypeDao().getAllRepairTypesDirect()

        // Selected repair is Samsung Galaxy S21
        val selectedBrand = "Samsung"
        val selectedModel = "Galaxy S21"

        val matchingPrices = allPrices.filter {
            it.brand.equals(selectedBrand, ignoreCase = true) &&
            it.model.equals(selectedModel, ignoreCase = true)
        }
        val suggestions = (matchingPrices.map { it.repairType.trim() } + allRepairTypes.map { it.name.trim() })
            .filter { it.isNotBlank() }
            .distinct()

        // Suggestions should contain Samsung specific items + master items
        org.junit.Assert.assertTrue(suggestions.contains("OLED Screen"))
        org.junit.Assert.assertTrue(suggestions.contains("Battery Replacement"))
        org.junit.Assert.assertTrue(suggestions.contains("Charging Port"))
        org.junit.Assert.assertTrue(suggestions.contains("Microphone"))

        // Unrelated Apple iPhone prices/types from other models should NOT be in brand/model matching prices
        val appleInMatching = matchingPrices.any { it.repairType == "iPhone Screen" }
        org.junit.Assert.assertFalse(appleInMatching)
    }

    @Test
    fun `test automatic price filling and changing repair type clears price if no price mapped`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        repository.savePrice("Samsung", "Galaxy S21", "Battery", 6500.0)

        // 1. Lookup Battery -> fills 6500.0
        val batteryPrice = repository.lookupPrice("Samsung", "Galaxy S21", "Battery")
        assertEquals(6500.0, batteryPrice!!, 0.001)

        // 2. Change repair type to "Camera Glass" (unmapped) -> returns null, price cleared
        val unmappedPrice = repository.lookupPrice("Samsung", "Galaxy S21", "Camera Glass")
        org.junit.Assert.assertNull(unmappedPrice)

        // 3. Allows Rs. 0 as explicitly saved price
        repository.savePrice("Samsung", "Galaxy S21", "Software Reset", 0.0)
        val zeroPrice = repository.lookupPrice("Samsung", "Galaxy S21", "Software Reset")
        assertEquals(0.0, zeroPrice!!, 0.001)
    }

    @Test
    fun `test save price for future jobs checkbox behavior in Add and Edit`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val brand = "Xiaomi"
        val model = "Redmi Note 11"

        // Initial default price in Price Manager is 4000
        repository.savePrice(brand, model, "Screen Glass", 4000.0)
        assertEquals(4000.0, repository.lookupPrice(brand, model, "Screen Glass")!!, 0.001)

        // Create a repair job
        val repair = repository.createRepair(
            customerName = "Nimal",
            customerPhone = "0770001122",
            brand = brand,
            model = model,
            imei = "123456789012345",
            deviceColour = "Black",
            accessories = "None",
            fault = "Cracked glass",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Screen Glass" to 4000.0),
            initialPaymentAmount = 0.0
        )

        val repairItems = db.repairItemDao().getItemsForRepairDirect(repair.id)
        val itemId = repairItems[0].id

        // Scenario 1: Edit item price to 3500 with saveForFutureJobs = FALSE
        // It updates the repair item, but does NOT change Price Manager default price
        repository.updateRepairItem(itemId, repair.id, "Screen Glass", 3500.0)
        val updatedItem = db.repairItemDao().getItemsForRepairDirect(repair.id)[0]
        assertEquals(3500.0, updatedItem.price, 0.001)
        // Price Manager default price MUST STILL BE 4000.0
        assertEquals(4000.0, repository.lookupPrice(brand, model, "Screen Glass")!!, 0.001)

        // Scenario 2: Edit item price to 4800 with saveForFutureJobs = TRUE
        repository.updateRepairItem(itemId, repair.id, "Screen Glass", 4800.0)
        repository.savePrice(brand, model, "Screen Glass", 4800.0)
        // Now Price Manager default price IS updated to 4800.0
        assertEquals(4800.0, repository.lookupPrice(brand, model, "Screen Glass")!!, 0.001)

        // Scenario 3: Add new custom item with saveForFutureJobs = TRUE
        repository.addRepairItem(repair.id, "Custom Speaker Mesh", 1200.0)
        repository.savePrice(brand, model, "Custom Speaker Mesh", 1200.0)
        repository.addRepairTypeIfNew("Custom Speaker Mesh")

        assertEquals(1200.0, repository.lookupPrice(brand, model, "Custom Speaker Mesh")!!, 0.001)
        org.junit.Assert.assertTrue(db.repairTypeDao().getAllRepairTypesDirect().any { it.name == "Custom Speaker Mesh" })

        // Check job total recalculation: 4800 + 1200 = 6000
        val refreshedRepair = db.repairDao().getRepairById(repair.id)
        assertEquals(6000.0, refreshedRepair!!.totalPrice, 0.001)
        assertEquals(6000.0, refreshedRepair.balance, 0.001)
    }

    @Test
    fun `test credit jobs tracking and payment updates`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        // 1. Create a repair job with Rs. 5000 price and Rs. 2000 initial payment -> Rs. 3000 balance
        val repair = repository.createRepair(
            customerName = "Sunil Perera",
            customerPhone = "0712345678",
            brand = "Apple",
            model = "iPhone 13",
            imei = "354892019283746",
            deviceColour = "Blue",
            accessories = "SIM Tray",
            fault = "Display replacement",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("OLED Display" to 5000.0),
            initialPaymentAmount = 2000.0
        )

        assertEquals("RECEIVED", repair.status)
        assertEquals(3000.0, repair.balance, 0.001)

        // It is NOT yet in credit jobs because status is RECEIVED (not DELIVERED)
        var allRepairs = db.repairDao().getAllRepairsDirect()
        var creditJobs = allRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
        org.junit.Assert.assertFalse(creditJobs.any { it.id == repair.id })

        // 2. Change repair status to DELIVERED while balance is Rs. 3000
        val deliveredRepair = repository.updateStatus(repair.id, "DELIVERED", "Delivered on credit")
        org.junit.Assert.assertNotNull(deliveredRepair)
        assertEquals("DELIVERED", deliveredRepair!!.status)
        assertEquals(3000.0, deliveredRepair.balance, 0.001)

        // Now it MUST be included in credit jobs
        allRepairs = db.repairDao().getAllRepairsDirect()
        creditJobs = allRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
        org.junit.Assert.assertTrue(creditJobs.any { it.id == repair.id })
        val trackedJob = creditJobs.first { it.id == repair.id }
        assertEquals("0712345678", trackedJob.customerPhone)
        assertEquals("Sunil Perera", trackedJob.customerName)
        assertEquals(5000.0, trackedJob.totalPrice, 0.001)
        assertEquals(2000.0, trackedJob.amountPaid, 0.001)
        assertEquals(3000.0, trackedJob.balance, 0.001)

        // 3. Customer pays an additional Rs. 1000 -> balance becomes 2000
        repository.addPayment(repair.id, 1000.0)
        allRepairs = db.repairDao().getAllRepairsDirect()
        val afterPartial = allRepairs.first { it.id == repair.id }
        assertEquals("DELIVERED", afterPartial.status) // Status remains DELIVERED
        assertEquals(3000.0, afterPartial.amountPaid, 0.001)
        assertEquals(2000.0, afterPartial.balance, 0.001)
        creditJobs = allRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
        org.junit.Assert.assertTrue(creditJobs.any { it.id == repair.id })

        // 4. Customer pays remaining Rs. 2000 -> balance becomes 0
        repository.addPayment(repair.id, 2000.0)
        allRepairs = db.repairDao().getAllRepairsDirect()
        val fullyPaid = allRepairs.first { it.id == repair.id }
        assertEquals("DELIVERED", fullyPaid.status) // Status still remains DELIVERED
        assertEquals(5000.0, fullyPaid.amountPaid, 0.001)
        assertEquals(0.0, fullyPaid.balance, 0.001)
        assertEquals("PAID", fullyPaid.paymentStatus)

        // Automatically removed from credit jobs when balance reaches Rs. 0
        creditJobs = allRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
        org.junit.Assert.assertFalse(creditJobs.any { it.id == repair.id })
    }

    @Test
    fun `test returning customer lookup and previous credit debt calculation`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val phone = "0771122334"

        // 1. Create Job 1: Delivered with balance 1500
        val r1 = repository.createRepair(
            customerName = "Kamal Silva",
            customerPhone = phone,
            brand = "Samsung",
            model = "A12",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Charging issue",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Charging Port" to 2500.0),
            initialPaymentAmount = 1000.0
        )
        repository.updateStatus(r1.id, "DELIVERED")

        // 2. Create Job 2: Delivered with balance 2000
        val r2 = repository.createRepair(
            customerName = "Kamal Silva",
            customerPhone = phone,
            brand = "Samsung",
            model = "A12",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Mic not working",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Microphone" to 2000.0),
            initialPaymentAmount = 0.0
        )
        repository.updateStatus(r2.id, "DELIVERED")

        // 3. Lookup customer history by phone
        val (cust, customerRepairs) = repository.getCustomerSummaryByPhone(phone)
        org.junit.Assert.assertNotNull(cust)
        assertEquals("Kamal Silva", cust!!.name)
        assertEquals(2, customerRepairs.size)

        // Previous credit jobs: both are DELIVERED with balance > 0
        val previousCreditJobs = customerRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
        assertEquals(2, previousCreditJobs.size)
        val previousCreditTotal = previousCreditJobs.sumOf { it.balance }
        assertEquals(3500.0, previousCreditTotal, 0.001) // 1500 + 2000 = 3500

        // 4. Create new job (Job 3) for Kamal: Rs. 3000 price, Rs. 1000 deposit -> balance = 2000
        val r3 = repository.createRepair(
            customerName = "Kamal Silva",
            customerPhone = phone,
            brand = "Samsung",
            model = "A52",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Battery drain",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Battery" to 3000.0),
            initialPaymentAmount = 1000.0
        )

        // New job's balance is separate (2000) and NOT altered by past debts
        assertEquals(2000.0, r3.balance, 0.001)

        // Total customer outstanding is past credit (3500) + new balance (2000) = 5500
        val totalCustomerDebt = previousCreditTotal + r3.balance
        assertEquals(5500.0, totalCustomerDebt, 0.001)
    }

    @Test
    fun `test edit customer name updates customer and repairs preserving all data`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val phone = "0776655443"
        val r = repository.createRepair(
            customerName = "Nimal Perera",
            customerPhone = phone,
            brand = "Apple",
            model = "iPhone 11",
            imei = "123456789012345",
            deviceColour = "Black",
            accessories = "Cover",
            fault = "Display cracked",
            technicianNotes = "Original LCD",
            remarks = "Urgent",
            repairItems = listOf("Screen Replacement" to 15000.0),
            initialPaymentAmount = 5000.0
        )

        // Update customer name to "Nimal P. Perera"
        val result = repository.updateCustomer(
            oldPhone = phone,
            newPhone = phone,
            newName = "Nimal P. Perera"
        )
        assertEquals(com.example.data.repository.UpdateCustomerResult.Success, result)

        val (cust, repairs) = repository.getCustomerSummaryByPhone(phone)
        assertNotNull(cust)
        assertEquals("Nimal P. Perera", cust!!.name)
        assertEquals(1, repairs.size)
        assertEquals("Nimal P. Perera", repairs[0].customerName)
        assertEquals(r.jobNumber, repairs[0].jobNumber)
        assertEquals(15000.0, repairs[0].totalPrice, 0.001)
        assertEquals(5000.0, repairs[0].amountPaid, 0.001)
        assertEquals(10000.0, repairs[0].balance, 0.001)
    }

    @Test
    fun `test edit customer phone number preserves all repairs and balances without duplicates`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val oldPhone = "0711122334"
        val newPhone = "0788899001"

        // Create repair 1 (Delivered with balance)
        val r1 = repository.createRepair(
            customerName = "Sunil Shantha",
            customerPhone = oldPhone,
            brand = "Xiaomi",
            model = "Redmi Note 10",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Speaker issue",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Speaker" to 3000.0),
            initialPaymentAmount = 1000.0
        )
        repository.updateStatus(r1.id, "DELIVERED")

        // Change phone number to newPhone
        val result = repository.updateCustomer(
            oldPhone = oldPhone,
            newPhone = newPhone,
            newName = "Sunil Shantha"
        )
        assertEquals(com.example.data.repository.UpdateCustomerResult.Success, result)

        // Old phone lookup returns no customer
        val (oldCust, oldRepairs) = repository.getCustomerSummaryByPhone(oldPhone)
        assertEquals(null, oldCust)
        assertEquals(0, oldRepairs.size)

        // New phone lookup returns customer and repair
        val (newCust, newRepairs) = repository.getCustomerSummaryByPhone(newPhone)
        assertNotNull(newCust)
        assertEquals("Sunil Shantha", newCust!!.name)
        assertEquals(newPhone, newCust.phone)
        assertEquals(1, newRepairs.size)
        assertEquals(r1.jobNumber, newRepairs[0].jobNumber)
        assertEquals("DELIVERED", newRepairs[0].status)
        assertEquals(2000.0, newRepairs[0].balance, 0.001)
    }

    @Test
    fun `test edit customer phone conflict prevents overwrite`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val phone1 = "0723344556"
        val phone2 = "0756677889"

        repository.createRepair(
            customerName = "Customer One",
            customerPhone = phone1,
            brand = "Oppo",
            model = "F11",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Battery",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Battery" to 2000.0),
            initialPaymentAmount = 2000.0
        )

        repository.createRepair(
            customerName = "Customer Two",
            customerPhone = phone2,
            brand = "Vivo",
            model = "Y20",
            imei = "",
            deviceColour = "",
            accessories = "",
            fault = "Charging",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Charging Port" to 1500.0),
            initialPaymentAmount = 1500.0
        )

        // Attempt to change Customer One's phone to Customer Two's phone
        val result = repository.updateCustomer(
            oldPhone = phone1,
            newPhone = phone2,
            newName = "Customer One Updated"
        )

        // Conflict safely detected
        org.junit.Assert.assertTrue(result is com.example.data.repository.UpdateCustomerResult.PhoneConflict)
        val conflict = result as com.example.data.repository.UpdateCustomerResult.PhoneConflict
        assertEquals("Customer Two", conflict.existingCustomerName)

        // Verify both customers are preserved unchanged
        val (c1, _) = repository.getCustomerSummaryByPhone(phone1)
        val (c2, _) = repository.getCustomerSummaryByPhone(phone2)
        assertEquals("Customer One", c1?.name)
        assertEquals("Customer Two", c2?.name)
    }

    @Test
    fun `test edit customer validation rejects empty or invalid fields`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        val phone = "0779988776"

        // Blank name
        val r1 = repository.updateCustomer(phone, phone, "   ")
        org.junit.Assert.assertTrue(r1 is com.example.data.repository.UpdateCustomerResult.ValidationError)

        // Blank phone
        val r2 = repository.updateCustomer(phone, "   ", "Valid Name")
        org.junit.Assert.assertTrue(r2 is com.example.data.repository.UpdateCustomerResult.ValidationError)

        // Phone with less than 3 digits
        val r3 = repository.updateCustomer(phone, "12", "Valid Name")
        org.junit.Assert.assertTrue(r3 is com.example.data.repository.UpdateCustomerResult.ValidationError)
    }

    @Test
    fun `test built-in themes configuration and distinct status colors`() {
        val classic = com.example.ui.theme.AppThemeConfig.UDM_CLASSIC_THEME
        val midnight = com.example.ui.theme.AppThemeConfig.MIDNIGHT_LUXURY_THEME
        val violet = com.example.ui.theme.AppThemeConfig.ROYAL_VIOLET_THEME

        // Names and modes
        assertEquals("UDM Classic", classic.name)
        assertEquals("Midnight Luxury", midnight.name)
        assertEquals("Royal Violet", violet.name)

        // Card corner radius distinct per theme
        assertEquals(12, classic.cardCornerRadiusDp)
        assertEquals(14, midnight.cardCornerRadiusDp)
        assertEquals(16, violet.cardCornerRadiusDp)

        // Semantic status colors must be distinct
        listOf(classic, midnight, violet).forEach { theme ->
            org.junit.Assert.assertNotEquals(theme.statusDelivered, theme.statusCancelled)
            org.junit.Assert.assertNotEquals(theme.statusReceived, theme.statusDelivered)
            org.junit.Assert.assertNotEquals(theme.paymentPaid, theme.paymentUnpaid)
        }
    }

    @Test
    fun `test theme selection persists in database across app restarts`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        // Ensure baseline is UDM Classic
        val current = repository.getSettings()
        repository.updateSettings(current.copy(themeName = com.example.ui.theme.AppThemeMode.UDM_CLASSIC.id))
        assertEquals("UDM_CLASSIC", repository.getSettings().themeName)

        // Select Midnight Luxury
        val updatedMidnight = repository.getSettings().copy(themeName = com.example.ui.theme.AppThemeMode.MIDNIGHT_LUXURY.id)
        repository.updateSettings(updatedMidnight)
        val storedMidnight = repository.getSettings()
        assertEquals("MIDNIGHT_LUXURY", storedMidnight.themeName)

        // Select Royal Violet
        val updatedViolet = repository.getSettings().copy(themeName = com.example.ui.theme.AppThemeMode.ROYAL_VIOLET.id)
        repository.updateSettings(updatedViolet)
        val storedViolet = repository.getSettings()
        assertEquals("ROYAL_VIOLET", storedViolet.themeName)

        // Reset to UDM Classic
        val resetClassic = repository.getSettings().copy(themeName = com.example.ui.theme.AppThemeMode.UDM_CLASSIC.id)
        repository.updateSettings(resetClassic)
        assertEquals("UDM_CLASSIC", repository.getSettings().themeName)
    }

    @Test
    fun `test json backup and restore preserves theme settings and maintains legacy compatibility`() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.AppDatabase.getDatabase(application)
        val repository = com.example.data.repository.RepairRepository(db, application)

        // Set Royal Violet theme
        val currentSettings = repository.getSettings()
        repository.updateSettings(currentSettings.copy(themeName = "ROYAL_VIOLET"))

        // Export data
        val backupData = repository.getAllDataForBackup()
        val jsonExport = com.example.util.BackupManager.exportToJson(backupData)

        val jsonObject = org.json.JSONObject(jsonExport)
        val settingsObj = jsonObject.getJSONObject("settings")
        assertEquals("ROYAL_VIOLET", settingsObj.getString("themeName"))

        // Test restoring modern backup
        val parsedModern = com.example.util.BackupManager.parseFromJson(jsonExport)
        assertEquals("ROYAL_VIOLET", parsedModern.settings.themeName)

        // Test legacy backup JSON without themeName
        val legacyJson = """
            {
                "version": 1,
                "exportedAt": 1690000000,
                "settings": {
                    "shopName": "Legacy Shop",
                    "shopPhone": "0770000000",
                    "shopAddress": "Test Address",
                    "whatsappNumber": "0770000000",
                    "receiptFooter": "Thank you",
                    "currency": "Rs.",
                    "startingJobNumber": 1,
                    "autoReadySms": true,
                    "smsTemplate": "Ready",
                    "autoRegistrationSms": true,
                    "registrationSmsTemplate": "Received",
                    "isDarkMode": true
                },
                "brands": [],
                "repairTypes": [],
                "models": [],
                "prices": [],
                "repairs": [],
                "items": [],
                "payments": [],
                "customers": [],
                "cancellations": []
            }
        """.trimIndent()

        val parsedLegacy = com.example.util.BackupManager.parseFromJson(legacyJson)
        // Safe default to UDM_CLASSIC
        assertEquals("UDM_CLASSIC", parsedLegacy.settings.themeName)
        assertEquals("Legacy Shop", parsedLegacy.settings.shopName)

        // Reset back to UDM_CLASSIC
        repository.updateSettings(repository.getSettings().copy(themeName = "UDM_CLASSIC"))
    }
}

