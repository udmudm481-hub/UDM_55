package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.RepairRepository
import com.example.data.repository.StockCheckResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockAvailabilityValidationTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: RepairRepository

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        repository = RepairRepository(db, app)
        val allStocks = repository.getAllStockItemsDirect()
        for (item in allStocks) {
            repository.deleteStockItem(item)
        }
    }

    @org.junit.After
    fun tearDown() = runBlocking {
        val allStocks = repository.getAllStockItemsDirect()
        for (item in allStocks) {
            repository.deleteStockItem(item)
        }
    }

    @Test
    fun testCaseA_createRepairJobWhenStockIsZero_savesSuccessfullyWithWarning() = runBlocking {
        // Setup: Create stock item "T2 Charging Pin" with quantity = 0
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 0,
            purchasePrice = 60.0,
            sellingPrice = 200.0,
            supplier = "Colombo Parts",
            notes = "Micro USB charging connector",
            lowStockWarning = 5
        )
        assertNotNull("Stock item must be created", stockItem)
        assertEquals("Initial stock quantity must be 0", 0, stockItem.quantity)

        // Connect T2 Charging Pin to Nokia 105 - Charging Pin (requires 1 unit)
        val conn = repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Charging Pin",
            quantityUsed = 1
        )
        assertNotNull("Stock connection must be created", conn)

        // Execute: Create new repair job requiring 1 T2 Charging Pin when stock is 0
        val repairJob = repository.createRepair(
            customerName = "Sunil Perera",
            customerPhone = "0771234567",
            brand = "Nokia",
            model = "Nokia 105",
            imei = "354987012345678",
            deviceColour = "Black",
            accessories = "Battery",
            fault = "Not charging",
            technicianNotes = "Check charging pin connector",
            remarks = "Customer waiting",
            repairItems = listOf("Charging Pin" to 1500.0),
            initialPaymentAmount = 0.0
        )

        // Verify: Job was saved successfully
        assertNotNull("Repair job must be saved successfully", repairJob)
        assertTrue("Repair job ID must be valid", repairJob.id > 0)
        assertEquals("Job status should start as RECEIVED (pending)", "RECEIVED", repairJob.status)
        assertEquals("Customer name must be preserved", "Sunil Perera", repairJob.customerName)
        assertEquals("Customer phone must be preserved", "0771234567", repairJob.customerPhone)
        assertEquals("Device model must be preserved", "Nokia 105", repairJob.model)
        assertEquals("Repair total price must be preserved", 1500.0, repairJob.totalPrice, 0.001)

        // Verify: Stock was NOT deducted on creation
        val stockAfterCreation = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock quantity must remain 0 (not negative or deducted)", 0, stockAfterCreation.quantity)

        // Verify: Stock check reports Insufficient with correct shortage details
        val checkResult = repository.checkStockForRepair(repairJob.id)
        assertTrue("Check result must be Insufficient", checkResult is StockCheckResult.Insufficient)
        val insufficient = checkResult as StockCheckResult.Insufficient
        assertEquals("Must report exactly 1 insufficient stock item", 1, insufficient.items.size)

        val itemInfo = insufficient.items[0]
        assertEquals("Stock item name must match", "T2 Charging Pin", itemInfo.stockItem.name)
        assertEquals("Available quantity must be 0", 0, itemInfo.available)
        assertEquals("Required quantity must be 1", 1, itemInfo.required)
        assertEquals("Shortage quantity must be 1", 1, itemInfo.shortage)
    }

    @Test
    fun testCaseB_closeWarningAndReopenSavedJob_preservesAllDetails() = runBlocking {
        // Setup stock item with 0 quantity and connection
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 0,
            purchasePrice = 50.0,
            sellingPrice = 180.0
        )
        repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Charging Pin",
            quantityUsed = 1
        )

        val created = repository.createRepair(
            customerName = "Nimal Silva",
            customerPhone = "0718889999",
            brand = "Nokia",
            model = "Nokia 105",
            imei = "998877665544332",
            deviceColour = "Blue",
            accessories = "SIM Tray",
            fault = "Charging pin broken",
            technicianNotes = "Pin loose on PCB",
            remarks = "Urgent repair",
            repairItems = listOf("Charging Pin" to 1200.0),
            initialPaymentAmount = 500.0
        )

        // Simulate closing warning and reopening the saved job by fetching directly from DB
        val retrieved = repository.getRepairById(created.id)
        assertNotNull("Saved repair job must exist in database", retrieved)
        assertEquals("Job number must match", created.jobNumber, retrieved!!.jobNumber)
        assertEquals("Customer name must match", "Nimal Silva", retrieved.customerName)
        assertEquals("Customer phone must match", "0718889999", retrieved.customerPhone)
        assertEquals("Brand must match", "Nokia", retrieved.brand)
        assertEquals("Model must match", "Nokia 105", retrieved.model)
        assertEquals("IMEI must match", "998877665544332", retrieved.imei)
        assertEquals("Device colour must match", "Blue", retrieved.deviceColour)
        assertEquals("Accessories must match", "SIM Tray", retrieved.accessories)
        assertEquals("Fault must match", "Charging pin broken", retrieved.fault)
        assertEquals("Technician notes must match", "Pin loose on PCB", retrieved.technicianNotes)
        assertEquals("Remarks must match", "Urgent repair", retrieved.remarks)
        assertEquals("Total price must match", 1200.0, retrieved.totalPrice, 0.001)
        assertEquals("Amount paid must match", 500.0, retrieved.amountPaid, 0.001)
        assertEquals("Balance must match", 700.0, retrieved.balance, 0.001)
        assertEquals("Payment status must match", "PARTIALLY PAID", retrieved.paymentStatus)

        // Verify items saved
        val items = repository.getItemsForRepairDirect(created.id)
        assertEquals("Should have 1 repair item", 1, items.size)
        assertEquals("Charging Pin", items[0].repairType)
        assertEquals(1200.0, items[0].price, 0.001)

        // Verify payment saved
        val payments = repository.getPaymentsForRepairDirect(created.id)
        assertEquals("Should have 1 payment record", 1, payments.size)
        assertEquals(500.0, payments[0].amount, 0.001)
    }

    @Test
    fun testCaseC_createRepairJobWhenStockIsSufficient_noInsufficientWarning() = runBlocking {
        // Setup: Create stock item with quantity = 10
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 10,
            purchasePrice = 50.0,
            sellingPrice = 150.0
        )
        repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Charging Pin",
            quantityUsed = 1
        )

        // Create repair job
        val repairJob = repository.createRepair(
            customerName = "Amara Senanayake",
            customerPhone = "0752223344",
            brand = "Nokia",
            model = "Nokia 105",
            fault = "Charging connector loose",
            repairItems = listOf("Charging Pin" to 1500.0)
        )
        assertNotNull(repairJob)

        // Verify: Check stock returns Ok (no insufficient-stock warning)
        val checkResult = repository.checkStockForRepair(repairJob.id)
        assertTrue("Check result must be Ok when stock is sufficient", checkResult is StockCheckResult.Ok)

        // Verify stock is not yet deducted on creation (only deducted when marked READY/DELIVERED)
        val currentStock = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock remains 10 until completed", 10, currentStock.quantity)
    }

    @Test
    fun testCaseD_attemptToMarkRepairReadyWhenStockIsInsufficient_protectionRemainsActive() = runBlocking {
        // Setup: Create stock item with quantity = 0
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 0,
            purchasePrice = 50.0,
            sellingPrice = 150.0
        )
        repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Charging Pin",
            quantityUsed = 1
        )

        val repairJob = repository.createRepair(
            customerName = "Kamal Gunaratne",
            customerPhone = "0779998877",
            brand = "Nokia",
            model = "Nokia 105",
            fault = "Charging dead",
            repairItems = listOf("Charging Pin" to 1500.0)
        )
        assertEquals("RECEIVED", repairJob.status)

        // Attempt to update status to READY without emergency adjustment
        val updatedRepair = repository.updateStatus(repairJob.id, "READY")
        assertNotNull(updatedRepair)

        // Verify: Status change to READY is blocked
        assertEquals("Repair status must remain RECEIVED, not transitioned to READY", "RECEIVED", updatedRepair!!.status)

        // Verify: Stock balance never becomes negative
        val stockAfterBlockedAttempt = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock quantity must remain 0 and NOT negative", 0, stockAfterBlockedAttempt.quantity)

        // Now replenish stock by adding 5 units
        repository.addStock(stockItem.id, 5, "Stock arrived from supplier")
        val replenishedStock = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock after replenishment must be 5", 5, replenishedStock.quantity)

        // Now mark repair READY with sufficient stock
        val completedRepair = repository.updateStatus(repairJob.id, "READY")
        assertNotNull(completedRepair)
        assertEquals("Status now transitions to READY", "READY", completedRepair!!.status)

        // Stock is now safely deducted from 5 to 4
        val stockAfterReady = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock decreases from 5 to 4 after repair is marked READY", 4, stockAfterReady.quantity)
    }

    @Test
    fun testCaseE_restartApp_savedJobAndStockQuantitiesRemainCorrect() = runBlocking {
        // Setup initial stock item with 0 quantity
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 0,
            purchasePrice = 45.0,
            sellingPrice = 160.0
        )
        repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Charging Pin",
            quantityUsed = 1
        )

        val repairJob = repository.createRepair(
            customerName = "Ruwan Bandara",
            customerPhone = "0701122334",
            brand = "Nokia",
            model = "Nokia 105",
            fault = "Charging issue",
            repairItems = listOf("Charging Pin" to 1500.0)
        )

        // Simulate app restart: re-instantiate RepairRepository from the same database instance
        val newRepositoryInstance = RepairRepository(db, app)

        // Verify saved job details after restart
        val reloadedJob = newRepositoryInstance.getRepairById(repairJob.id)
        assertNotNull("Job must be retrieved successfully after restart", reloadedJob)
        assertEquals("Job number must match after restart", repairJob.jobNumber, reloadedJob!!.jobNumber)
        assertEquals("Customer name must match after restart", "Ruwan Bandara", reloadedJob.customerName)
        assertEquals("Customer phone must match after restart", "0701122334", reloadedJob.customerPhone)
        assertEquals("Status must remain RECEIVED after restart", "RECEIVED", reloadedJob.status)

        // Verify stock item after restart
        val reloadedStock = newRepositoryInstance.getStockItemById(stockItem.id)
        assertNotNull("Stock item must be retrieved after restart", reloadedStock)
        assertEquals("T2 Charging Pin", reloadedStock!!.name)
        assertEquals("Stock quantity must remain 0 after restart", 0, reloadedStock.quantity)

        // Verify shortage check works identically after restart
        val check = newRepositoryInstance.checkStockForRepair(repairJob.id)
        assertTrue(check is StockCheckResult.Insufficient)
        val shortageInfo = (check as StockCheckResult.Insufficient).items[0]
        assertEquals(0, shortageInfo.available)
        assertEquals(1, shortageInfo.required)
        assertEquals(1, shortageInfo.shortage)
    }

    @Test
    fun testZeroPriceRepairItem_preservesZeroPriceAndChecksStock() = runBlocking {
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 0,
            purchasePrice = 50.0,
            sellingPrice = 0.0
        )
        repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Warranty Charging Pin",
            quantityUsed = 1
        )

        // Create repair with price Rs. 0
        val zeroPriceRepair = repository.createRepair(
            customerName = "Warranty Customer",
            customerPhone = "0775556677",
            brand = "Nokia",
            model = "Nokia 105",
            fault = "Warranty check",
            repairItems = listOf("Warranty Charging Pin" to 0.0)
        )
        assertNotNull(zeroPriceRepair)
        assertEquals(0.0, zeroPriceRepair.totalPrice, 0.001)

        val check = repository.checkStockForRepair(zeroPriceRepair.id)
        assertTrue(check is StockCheckResult.Insufficient)
        assertEquals(1, (check as StockCheckResult.Insufficient).items[0].shortage)
    }

    @Test
    fun testSharedStock_connectedToMultipleModels_trackedCorrectly() = runBlocking {
        // T2 Charging Pin connected to Nokia 105, Greentel Keypad, and Samsung B110
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 1,
            purchasePrice = 50.0,
            sellingPrice = 150.0
        )
        repository.saveStockConnection(stockItem.id, "Nokia", "Nokia 105", "Charging Pin", 1)
        repository.saveStockConnection(stockItem.id, "Chinese", "Greentel Keypad", "Charging Pin", 1)
        repository.saveStockConnection(stockItem.id, "Samsung", "Samsung B110", "Charging Pin", 1)

        // Job 1: Nokia 105 with 1 available
        val job1 = repository.createRepair(
            customerName = "Customer 1",
            customerPhone = "0771111111",
            brand = "Nokia",
            model = "Nokia 105",
            repairItems = listOf("Charging Pin" to 1500.0)
        )
        // Since stock is 1, Job 1 check is OK
        assertTrue(repository.checkStockForRepair(job1.id) is StockCheckResult.Ok)

        // Mark Job 1 as READY -> consumes the 1 available unit
        repository.updateStatus(job1.id, "READY")
        assertEquals(0, repository.getStockItemById(stockItem.id)!!.quantity)

        // Job 2: Greentel Keypad created when stock is now 0
        val job2 = repository.createRepair(
            customerName = "Customer 2",
            customerPhone = "0772222222",
            brand = "Chinese",
            model = "Greentel Keypad",
            repairItems = listOf("Charging Pin" to 1200.0)
        )
        // Job 2 saves successfully!
        assertNotNull(job2)
        assertEquals("RECEIVED", job2.status)

        // Job 2 check shows Insufficient (available: 0, required: 1, shortage: 1)
        val checkJob2 = repository.checkStockForRepair(job2.id)
        assertTrue(checkJob2 is StockCheckResult.Insufficient)
        val info2 = (checkJob2 as StockCheckResult.Insufficient).items[0]
        assertEquals("T2 Charging Pin", info2.stockItem.name)
        assertEquals(0, info2.available)
        assertEquals(1, info2.required)
        assertEquals(1, info2.shortage)
    }
}
