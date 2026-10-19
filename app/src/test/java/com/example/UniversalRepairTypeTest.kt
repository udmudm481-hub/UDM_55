package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.UniversalRepairTypeEntity
import com.example.data.repository.RepairRepository
import com.example.util.BackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UniversalRepairTypeTest {

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
    fun testVerificationWorkflow_A_to_F() = runBlocking {
        val typeName = "Virus Remove and Virus Guard"
        val defaultPrice = 500.0

        // Step A: Create "Virus Remove and Virus Guard" with a default price of Rs. 500
        repository.saveUniversalRepairType(typeName, defaultPrice)

        val universalList = repository.allUniversalRepairTypes.first()
        val created = universalList.find { it.name == typeName }
        assertNotNull("Universal repair type should be created", created)
        assertEquals("Default price should be 500.0", 500.0, created!!.defaultPrice, 0.001)

        // Step B: Select it for Samsung M02 and verify that Rs. 500 is filled automatically
        val samsungPrice = repository.lookupPrice("Samsung", "Galaxy M02", typeName)
        assertEquals("Samsung M02 should resolve universal default price Rs. 500", 500.0, samsungPrice!!, 0.001)

        // Step C: Select it for Redmi 9 and verify that Rs. 500 is filled automatically
        val redmiPrice = repository.lookupPrice("Redmi", "Redmi 9", typeName)
        assertEquals("Redmi 9 should resolve universal default price Rs. 500", 500.0, redmiPrice!!, 0.001)

        // Step D: Configure a different model-specific price and verify that the exact model-specific price takes priority
        // Configure Samsung M02 with model-specific price of Rs. 750
        val modelSpecificPrice = 750.0
        repository.savePrice("Samsung", "Galaxy M02", typeName, modelSpecificPrice)

        // Samsung M02 must now return 750 (Priority 1: exact Brand + Model + Repair Type)
        val samsungUpdatedPrice = repository.lookupPrice("Samsung", "Galaxy M02", typeName)
        assertEquals("Exact model-specific price should take priority for Samsung M02", 750.0, samsungUpdatedPrice!!, 0.001)

        // Redmi 9 must still return universal default 500 (Priority 2: Universal Repair Type)
        val redmiStillUniversal = repository.lookupPrice("Redmi", "Redmi 9", typeName)
        assertEquals("Redmi 9 must still resolve universal default price Rs. 500", 500.0, redmiStillUniversal!!, 0.001)

        // Step E: Change an individual repair item's price without updating the universal default and verify that the default remains Rs. 500
        val repair = repository.createRepair(
            customerName = "Amila Silva",
            customerPhone = "0771234567",
            brand = "Redmi",
            model = "Redmi 9",
            imei = "123456789012345",
            deviceColour = "Blue",
            accessories = "None",
            fault = "Virus infected",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf(typeName to 650.0), // Overridden price for this specific job item
            initialPaymentAmount = 0.0
        )
        assertNotNull(repair)
        assertEquals(650.0, repair!!.totalPrice, 0.001)

        // Verify universal default remains Rs. 500
        val universalAfterOverride = repository.lookupPrice("POCO", "POCO M3", typeName)
        assertEquals("Universal default must remain Rs. 500 after individual job item override", 500.0, universalAfterOverride!!, 0.001)

        val universalEntity = db.universalRepairTypeDao().findByName(typeName)
        assertNotNull(universalEntity)
        assertEquals("Universal entity default price in database must remain 500.0", 500.0, universalEntity!!.defaultPrice, 0.001)

        // Step F: Restart app (simulate re-opening database instance) and verify that universal repair type and price are still available
        val dbInstance2 = AppDatabase.getDatabase(app)
        val repo2 = RepairRepository(dbInstance2, app)
        val persistedPrice = repo2.lookupPrice("Redmi", "Redmi 9", typeName)
        assertEquals("Universal repair type price must persist across app restart", 500.0, persistedPrice!!, 0.001)
    }

    @Test
    fun testZeroRupeePriceIsPreserved() = runBlocking {
        // Rs. 0 repair price (warranty service or free checkup) is valid and preserved
        val freeTypeName = "Free Warranty Inspection"
        repository.saveUniversalRepairType(freeTypeName, 0.0)

        val price = repository.lookupPrice("Apple", "iPhone 13", freeTypeName)
        assertNotNull("Rs. 0 price must be found", price)
        assertEquals("Price must be exactly 0.0", 0.0, price!!, 0.001)
    }

    @Test
    fun testUniversalRepairTypeDeletionDoesNotAffectExistingRepairs() = runBlocking {
        val typeName = "Software Flash"
        repository.saveUniversalRepairType(typeName, 1200.0)

        // Create repair using this type
        val repair = repository.createRepair(
            customerName = "Kasun Dias",
            customerPhone = "0779876543",
            brand = "Xiaomi",
            model = "Xiaomi 12",
            imei = "987654321098765",
            deviceColour = "Black",
            accessories = "None",
            fault = "Bootloop",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf(typeName to 1200.0),
            initialPaymentAmount = 0.0
        )
        assertNotNull(repair)

        // Delete universal repair type
        val entity = db.universalRepairTypeDao().findByName(typeName)
        assertNotNull(entity)
        repository.deleteUniversalRepairType(entity!!)

        // Verify universal repair type is deleted
        val deletedEntity = db.universalRepairTypeDao().findByName(typeName)
        assertNull("Universal repair type should be deleted", deletedEntity)

        // Verify existing repair record and price is preserved
        val existingRepair = repository.getRepairById(repair!!.id)
        assertNotNull("Existing repair must be preserved", existingRepair)
        assertEquals(1200.0, existingRepair!!.totalPrice, 0.001)
    }

    @Test
    fun testBackupAndRestorePreservesUniversalRepairTypes() = runBlocking {
        val typeName = "Tempered Glass Installation"
        repository.saveUniversalRepairType(typeName, 350.0)

        // Export data
        val backupData = repository.getAllDataForBackup()
        assertTrue("Backup must contain universal repair type", backupData.universalRepairTypes.any { it.name == typeName })

        val json = BackupManager.exportToJson(backupData)
        assertTrue(json.contains("universalRepairTypes"))
        assertTrue(json.contains(typeName))

        // Clear and restore
        val validation = BackupManager.validateJson(json)
        assertTrue(validation is com.example.util.BackupValidationResult.Valid)
        val validData = (validation as com.example.util.BackupValidationResult.Valid).data

        repository.restoreData(validData, clearExisting = true)

        val restoredPrice = repository.lookupPrice("Vivo", "V21", typeName)
        assertEquals("Restored universal repair type must retain price Rs. 350", 350.0, restoredPrice!!, 0.001)
    }
}
