package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.StockItemEntity
import com.example.data.repository.RepairRepository
import com.example.data.repository.StockCheckResult
import com.example.util.BackupManager
import kotlinx.coroutines.flow.first
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
class StockManagementTest {

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
    fun testAllSixStockAcceptanceScenarios() = runBlocking {
        // --- Test 1: Add Stock Item & Connect to Multiple Repair Types/Models ---
        val stockItem = repository.createStockItem(
            name = "T2 Charging Pin",
            quantity = 10,
            purchasePrice = 50.0,
            sellingPrice = 150.0,
            supplier = "Colombo Parts",
            notes = "Standard micro-USB connector",
            lowStockWarning = 2
        )
        assertNotNull("Stock item should be created", stockItem)
        assertEquals("Initial stock should be 10", 10, stockItem.quantity)
        assertEquals("Low stock warning should be 2", 2, stockItem.lowStockWarning)

        // Connect T2 Charging Pin to Nokia 105 - Charging Pin
        val connNokia = repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Nokia",
            model = "Nokia 105",
            repairType = "Charging Pin",
            quantityUsed = 1
        )
        assertNotNull(connNokia)

        // Connect same T2 Charging Pin to Greentel Keypad - Charging Pin
        val connGreentel = repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Chinese",
            model = "Greentel Keypad",
            repairType = "Charging Pin",
            quantityUsed = 1
        )
        assertNotNull(connGreentel)

        // Connect same T2 Charging Pin to Samsung Another Model - Charging Pin
        val connSamsung = repository.saveStockConnection(
            stockItemId = stockItem.id,
            brand = "Samsung",
            model = "Another compatible model",
            repairType = "Charging Pin",
            quantityUsed = 1
        )
        assertNotNull(connSamsung)

        // Verify all 3 connections point to the SAME stock item record
        val connections = repository.getConnectionsForStockItemDirect(stockItem.id)
        assertEquals("Should have 3 connections for T2 Charging Pin", 3, connections.size)
        assertTrue(connections.all { it.stockItemId == stockItem.id })

        // Check stock item balance is still 10 (not split into multiple balances)
        val refreshedStock = repository.getStockItemById(stockItem.id)!!
        assertEquals("Single stock balance should remain 10", 10, refreshedStock.quantity)

        // --- Test 2: Complete a repair with Nokia 105 - Charging Pin ---
        val nokiaRepair = repository.createRepair(
            customerName = "Sunil Perera",
            customerPhone = "0771234567",
            brand = "Nokia",
            model = "Nokia 105",
            fault = "Charging Issue",
            repairItems = listOf("Charging Pin" to 1500.0)
        )
        // Verify stock is NOT deducted on creation / initial status
        var stockAfterCreation = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock should NOT be deducted while RECEIVED", 10, stockAfterCreation.quantity)

        // Change status to REPAIRING - stock still should not be deducted
        repository.updateStatus(nokiaRepair.id, "REPAIRING")
        val stockAfterRepairing = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock should NOT be deducted while REPAIRING", 10, stockAfterRepairing.quantity)

        // Now complete the Nokia repair by marking it READY
        val completedNokia = repository.updateStatus(nokiaRepair.id, "READY")
        assertNotNull(completedNokia)
        assertEquals("READY", completedNokia!!.status)

        // Verify stock decreases from 10 to 9
        val stockAfterNokia = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock should decrease to 9 after completing Nokia repair", 9, stockAfterNokia.quantity)

        // Verify stock movement history
        val movementsAfterNokia = repository.getMovementsForStockItemDirect(stockItem.id)
        assertTrue(movementsAfterNokia.any { it.quantityChange == -1 && it.resultingQuantity == 9 && it.repairId == nokiaRepair.id })

        // Repeated status updates to READY or DELIVERED must NOT duplicate deductions
        repository.updateStatus(nokiaRepair.id, "READY")
        repository.updateStatus(nokiaRepair.id, "DELIVERED")
        val stockAfterRedundantUpdates = repository.getStockItemById(stockItem.id)!!
        assertEquals("Duplicate status updates must NOT deduct stock twice", 9, stockAfterRedundantUpdates.quantity)

        // --- Test 3: Complete a repair with Greentel Keypad - Charging Pin ---
        val greentelRepair = repository.createRepair(
            customerName = "Kamal Silva",
            customerPhone = "0719876543",
            brand = "Chinese",
            model = "Greentel Keypad",
            fault = "No Charging",
            repairItems = listOf("Charging Pin" to 1200.0)
        )
        // Complete the Greentel repair by marking it READY
        repository.updateStatus(greentelRepair.id, "READY")

        // Verify same stock item balance decreases from 9 to 8
        val stockAfterGreentel = repository.getStockItemById(stockItem.id)!!
        assertEquals("Same stock balance should decrease from 9 to 8", 8, stockAfterGreentel.quantity)

        val movementsAfterGreentel = repository.getMovementsForStockItemDirect(stockItem.id)
        assertTrue(movementsAfterGreentel.any { it.quantityChange == -1 && it.resultingQuantity == 8 && it.repairId == greentelRepair.id })

        // --- Test 4: Reversing / Cancelling / Changing Status restores stock exactly once ---
        // Cancel the Greentel repair
        repository.updateStatus(greentelRepair.id, "CANCELLED", notes = "Customer cancelled repair")

        // Expected: Stock restores to 9 exactly once
        val stockAfterCancel = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock should restore to 9 when repair is CANCELLED", 9, stockAfterCancel.quantity)

        // Moving back to CANCELLED again should not double-restore
        repository.updateStatus(greentelRepair.id, "CANCELLED", notes = "Double cancel check")
        val stockAfterDoubleCancel = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock must not double restore", 9, stockAfterDoubleCancel.quantity)

        // Also test item removal restoration:
        // Nokia repair is currently READY (stock deducted = 1)
        val nokiaItems = repository.getItemsForRepairDirect(nokiaRepair.id)
        val nokiaChargingItem = nokiaItems.first()
        repository.removeRepairItem(nokiaChargingItem.id, nokiaRepair.id)

        // Expected: Stock restores from 9 back to 10
        val stockAfterItemRemoval = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock should restore to 10 when deducted repair item is removed", 10, stockAfterItemRemoval.quantity)

        // --- Test 5: Insufficient Stock Warning ---
        // Reduce stock to 0 using removeStock
        repository.removeStock(stockItem.id, 10, "Deplete for test")
        val depletedStock = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock should be 0", 0, depletedStock.quantity)

        // Create a new repair for Nokia 105 - Charging Pin (needs 1 unit)
        val urgentRepair = repository.createRepair(
            customerName = "Amara Dias",
            customerPhone = "0751112233",
            brand = "Nokia",
            model = "Nokia 105",
            fault = "Charging pin broken",
            repairItems = listOf("Charging Pin" to 1500.0)
        )

        // Check stock for this repair
        val checkResult = repository.checkStockForRepair(urgentRepair.id)
        assertTrue("Check stock should indicate Insufficient", checkResult is StockCheckResult.Insufficient)
        val insufficient = checkResult as StockCheckResult.Insufficient
        assertEquals(1, insufficient.items.size)
        assertEquals("T2 Charging Pin", insufficient.items[0].stockItem.name)
        assertEquals(0, insufficient.items[0].available)
        assertEquals(1, insufficient.items[0].required)

        // Normal update to READY with no emergency adjustment should NOT deduct stock below 0
        repository.updateStatus(urgentRepair.id, "READY")
        val stockAfterPreventedDeduction = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock must NEVER silently become negative", 0, stockAfterPreventedDeduction.quantity)

        // With controlled emergency adjustment:
        repository.updateStatus(urgentRepair.id, "READY", emergencyAdjustmentReason = "Controlled physical restock verified")
        val stockAfterEmergency = repository.getStockItemById(stockItem.id)!!
        assertEquals("Stock quantity remains non-negative after controlled adjustment and deduction", 0, stockAfterEmergency.quantity)

        // Movement history records controlled adjustment
        val urgentMovements = repository.getMovementsForStockItemDirect(stockItem.id)
        assertTrue(urgentMovements.any { it.reason.contains("Controlled stock adjustment") })

        // --- Test 6: Backup & Restore Preserves Stock Items, Connections, and History ---
        // Add 5 units back
        repository.addStock(stockItem.id, 5, "Restock arrival")
        val stockBeforeBackup = repository.getStockItemById(stockItem.id)!!
        assertEquals(5, stockBeforeBackup.quantity)

        val backupData = repository.getAllDataForBackup()
        val backupJson = BackupManager.exportToJson(backupData)

        // Verify backup JSON contains stockItems, stockConnections, stockMovements
        assertTrue("Backup must contain stockItems", backupJson.contains("stockItems"))
        assertTrue("Backup must contain stockConnections", backupJson.contains("stockConnections"))
        assertTrue("Backup must contain stockMovements", backupJson.contains("stockMovements"))
        assertTrue("Backup must contain T2 Charging Pin", backupJson.contains("T2 Charging Pin"))

        // Clear and restore
        val parsedBackup = BackupManager.parseFromJson(backupJson)
        repository.restoreData(parsedBackup, clearExisting = true)

        // Verify stock items preserved
        val restoredStockItems = repository.getAllStockItemsDirect()
        assertEquals("Restored stock items count", 1, restoredStockItems.size)
        val restoredItem = restoredStockItems[0]
        assertEquals("T2 Charging Pin", restoredItem.name)
        assertEquals(5, restoredItem.quantity)
        assertEquals(2, restoredItem.lowStockWarning)
        assertEquals("Colombo Parts", restoredItem.supplier)

        // Verify stock connections preserved
        val restoredConnections = repository.getConnectionsForStockItemDirect(restoredItem.id)
        assertEquals("Restored connections count should be 3", 3, restoredConnections.size)

        // Verify stock movement history preserved
        val restoredMovements = repository.getMovementsForStockItemDirect(restoredItem.id)
        assertTrue("Stock movements should be preserved", restoredMovements.isNotEmpty())
    }
}
