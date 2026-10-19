package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.CustomerEntity
import com.example.data.repository.RepairRepository
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
class CustomerDeletionTest {

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
    fun testDeleteCustomerWithoutRepairHistory() = runBlocking {
        // Given: A customer created without any repair jobs
        val phone = "0711111111"
        val name = "Standalone Customer"
        db.customerDao().insertCustomer(
            CustomerEntity(
                phone = phone,
                name = name,
                lastVisitTimestamp = System.currentTimeMillis()
            )
        )

        val customerBefore = db.customerDao().getCustomerByPhone(phone)
        assertNotNull(customerBefore)
        assertEquals(name, customerBefore!!.name)

        // When: Customer is deleted
        repository.deleteCustomer(phone)

        // Then: Customer is completely removed from customer directory
        val customerAfter = db.customerDao().getCustomerByPhone(phone)
        assertNull(customerAfter)

        val allCustomers = repository.allCustomers.first()
        assertTrue(allCustomers.none { it.phone == phone })
    }

    @Test
    fun testDeleteCustomerWithRepairHistoryPreservesJobsAndPayments() = runBlocking {
        // Given: Customer A with repair history and payments
        val phoneA = "0772222222"
        val nameA = "Nuwan Perera"
        val repairA = repository.createRepair(
            customerName = nameA,
            customerPhone = phoneA,
            brand = "Apple",
            model = "iPhone 13",
            imei = "354987102938475",
            deviceColour = "Midnight",
            accessories = "Back Cover",
            fault = "Display flickering",
            technicianNotes = "Replaced OLED",
            remarks = "Warranty 30 days",
            repairItems = listOf("OLED Display" to 25000.0, "Service Fee" to 3000.0),
            initialPaymentAmount = 10000.0
        )
        assertNotNull(repairA)
        val repairIdA = repairA.id

        // Add additional payment
        repository.addPayment(repairIdA, 5000.0)

        // Given: Customer B (other customer) to verify independence
        val phoneB = "0773333333"
        val nameB = "Kamal Silva"
        val repairB = repository.createRepair(
            customerName = nameB,
            customerPhone = phoneB,
            brand = "Samsung",
            model = "Galaxy A54",
            imei = "987654321098765",
            deviceColour = "Awesome Violet",
            accessories = "SIM Tray",
            fault = "Charging port issue",
            technicianNotes = "Port replaced",
            remarks = "None",
            repairItems = listOf("Charging Port" to 4500.0),
            initialPaymentAmount = 4500.0
        )
        assertNotNull(repairB)

        // Verify initial state
        val customersBefore = repository.allCustomers.first()
        assertTrue(customersBefore.any { it.phone == phoneA })
        assertTrue(customersBefore.any { it.phone == phoneB })

        val paymentsBefore = repository.getPaymentsForRepairDirect(repairIdA)
        assertEquals(2, paymentsBefore.size)
        assertEquals(15000.0, paymentsBefore.sumOf { it.amount }, 0.001)

        val repairBefore = repository.getRepairById(repairIdA)!!
        assertEquals(28000.0, repairBefore.totalPrice, 0.001)
        assertEquals(15000.0, repairBefore.amountPaid, 0.001)
        assertEquals(13000.0, repairBefore.balance, 0.001)

        // When: Deleting Customer A from Customers tab
        repository.deleteCustomer(phoneA)

        // Then 1: Customer A is removed from normal customers list
        val customersAfter = repository.allCustomers.first()
        assertTrue("Deleted customer must not be present in customers list", customersAfter.none { it.phone == phoneA })
        assertNull(db.customerDao().getCustomerByPhone(phoneA))

        // Then 2: Customer B is completely untouched and unchanged
        assertTrue("Other customer must remain in customers list", customersAfter.any { it.phone == phoneB })
        val directCustomerB = db.customerDao().getCustomerByPhone(phoneB)
        assertNotNull(directCustomerB)
        assertEquals(nameB, directCustomerB!!.name)

        // Then 3: Customer A's repair job, job number, customer details, and items are 100% preserved
        val repairAfter = repository.getRepairById(repairIdA)
        assertNotNull("Repair record must not be deleted", repairAfter)
        assertEquals(repairA.jobNumber, repairAfter!!.jobNumber)
        assertEquals(nameA, repairAfter.customerName)
        assertEquals(phoneA, repairAfter.customerPhone)
        assertEquals(28000.0, repairAfter.totalPrice, 0.001)
        assertEquals(15000.0, repairAfter.amountPaid, 0.001)
        assertEquals(13000.0, repairAfter.balance, 0.001)

        // Then 4: Customer A's payment transactions are 100% preserved
        val paymentsAfter = repository.getPaymentsForRepairDirect(repairIdA)
        assertEquals(2, paymentsAfter.size)
        assertEquals(15000.0, paymentsAfter.sumOf { it.amount }, 0.001)

        // Then 5: Status history is preserved
        val historyAfter = repository.getHistoryForRepair(repairIdA).first()
        assertTrue("Status history must be preserved", historyAfter.isNotEmpty())

        // Then 6: Customer B's repair job is completely untouched
        val repairBAfter = repository.getRepairById(repairB.id)!!
        assertEquals(nameB, repairBAfter.customerName)
        assertEquals(phoneB, repairBAfter.customerPhone)
        assertEquals(4500.0, repairBAfter.totalPrice, 0.001)
    }

    @Test
    fun testCustomerWithOutstandingBalanceRetainsDebtAfterDeletion() = runBlocking {
        // Given: Customer with an outstanding credit balance
        val phone = "0774444444"
        val name = "Saman Kumara"
        val repair = repository.createRepair(
            customerName = name,
            customerPhone = phone,
            brand = "Xiaomi",
            model = "Redmi Note 12",
            imei = "112233445566778",
            deviceColour = "Onyx Gray",
            accessories = "",
            fault = "Battery drain",
            technicianNotes = "New battery installed",
            remarks = "Credit customer",
            repairItems = listOf("Battery" to 6000.0),
            initialPaymentAmount = 2000.0
        )
        assertNotNull(repair)
        val repairId = repair.id

        // Deliver repair as a credit job with outstanding balance 4,000
        val deliveredRepair = repository.updateStatus(repairId, "DELIVERED")
        assertNotNull(deliveredRepair)
        assertEquals("DELIVERED", deliveredRepair!!.status)
        assertEquals(4000.0, deliveredRepair.balance, 0.001)

        // Total outstanding debt in all repairs before deletion
        val allRepairsBefore = repository.allRepairs.first()
        val debtBefore = allRepairsBefore.filter { it.customerPhone == phone }.sumOf { it.balance }
        assertEquals(4000.0, debtBefore, 0.001)

        // When: Customer entry is deleted
        repository.deleteCustomer(phone)

        // Then: Customer is removed from customers directory
        val allCustomers = repository.allCustomers.first()
        assertTrue(allCustomers.none { it.phone == phone })

        // Then: The debt and repair records remain exactly intact
        val allRepairsAfter = repository.allRepairs.first()
        val matchingRepairs = allRepairsAfter.filter { it.customerPhone == phone }
        assertEquals(1, matchingRepairs.size)
        assertEquals(4000.0, matchingRepairs[0].balance, 0.001)
        assertEquals("DELIVERED", matchingRepairs[0].status)
        assertEquals(repair.jobNumber, matchingRepairs[0].jobNumber)

        // Direct fetch
        val directRepair = repository.getRepairById(repairId)!!
        assertEquals(4000.0, directRepair.balance, 0.001)
        assertEquals(2000.0, directRepair.amountPaid, 0.001)
        assertEquals(6000.0, directRepair.totalPrice, 0.001)
    }

    @Test
    fun testCancelLeavesAllCustomerDataUnchanged() = runBlocking {
        // Given: Customer with records
        val phone = "0775555555"
        val name = "Chathura Senanayake"
        val repair = repository.createRepair(
            customerName = name,
            customerPhone = phone,
            brand = "Vivo",
            model = "Y20",
            imei = "998877665544332",
            deviceColour = "Blue",
            accessories = "",
            fault = "Mic not working",
            technicianNotes = "Fixed",
            remarks = "",
            repairItems = listOf("Microphone" to 2500.0),
            initialPaymentAmount = 2500.0
        )
        assertNotNull(repair)

        // Cancel simulated: no delete action invoked
        // Verify customer and repair are unchanged
        val customer = db.customerDao().getCustomerByPhone(phone)
        assertNotNull(customer)
        assertEquals(name, customer!!.name)

        val allCustomers = repository.allCustomers.first()
        assertTrue(allCustomers.any { it.phone == phone })

        val directRepair = repository.getRepairById(repair.id)
        assertNotNull(directRepair)
        assertEquals(phone, directRepair!!.customerPhone)
    }
}
