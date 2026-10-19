package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.repository.RepairRepository
import com.example.printer.CustomerReceiptConfig
import com.example.printer.P50SPrintingSystemSettings
import com.example.printer.PaymentReceiptConfig
import com.example.printer.PrinterPreferences
import com.example.printer.ReceiptAlignment
import com.example.printer.ReceiptBitmapGenerator
import com.example.printer.ReceiptDividerStyle
import com.example.printer.ReceiptElement
import com.example.printer.ReceiptElementType
import com.example.printer.ReceiptLogoSize
import com.example.printer.ReceiptPreferences
import com.example.printer.ReceiptTemplate
import com.example.printer.ReceiptTextSize
import com.example.ui.viewmodel.RepairViewModel
import com.example.util.BackupFileHelper
import com.example.util.BackupManager
import com.example.util.BackupValidationResult
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptDesignBackupRestoreTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: RepairRepository

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        repository = RepairRepository(db, app)
        repository.updateSettings(AppSettingsEntity())
        ReceiptPreferences.resetCustomerReceiptConfig(app)
        ReceiptPreferences.resetPaymentReceiptConfig(app)
        ReceiptPreferences.resetCustomerTemplate(app)
        ReceiptPreferences.resetPaymentTemplate(app)
        PrinterPreferences.resetP50SSettings(app)
        PrinterPreferences.setAutoPrintEnabled(app, false)
        PrinterPreferences.setCustomerReceiptDiagnosticMode(app, false)
    }

    /**
     * Helper to create a dummy valid PNG file for testing logo backup & restore
     */
    private fun createSampleLogoFile(fileName: String): File {
        val logoDir = File(app.filesDir, "receipt_logos").apply { if (!exists()) mkdirs() }
        val logoFile = File(logoDir, fileName)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        FileOutputStream(logoFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return logoFile
    }

    /**
     * Acceptance Test Cycle:
     * A. Customize the customer receipt design and payment receipt design.
     * B. Change the header, footer, font size, alignment, and other supported settings.
     * C. Add a custom logo.
     * D. Export a JSON backup.
     * E. Change the receipt designs in the app.
     * F. Restore the backup.
     * Expected: Both receipt designs and all backed-up settings return to their previous configurations.
     */
    @Test
    fun `test Acceptance Criteria A to F - customize, add logo, export, modify, restore and verify previous configuration`() = runBlocking {
        // Step A, B, C: Customize designs
        val sampleLogo = createSampleLogoFile("test_shop_logo.png")
        assertTrue("Logo file must exist initially", sampleLogo.exists())

        val customCustomerElements = listOf(
            ReceiptElement(
                type = ReceiptElementType.IMAGE_LOGO,
                logoPath = sampleLogo.absolutePath,
                logoSize = ReceiptLogoSize.LARGE,
                alignment = ReceiptAlignment.CENTER,
                spacingBottom = 8
            ),
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                text = "SPECIAL CUSTOM HEADER",
                fontSize = ReceiptTextSize.LARGE,
                isBold = true,
                alignment = ReceiptAlignment.CENTER,
                spacingBottom = 6
            ),
            ReceiptElement(
                type = ReceiptElementType.DYNAMIC_FIELD,
                fieldTag = "{SHOP_NAME}",
                fontSize = ReceiptTextSize.LARGE,
                isBold = true,
                alignment = ReceiptAlignment.CENTER
            ),
            ReceiptElement(
                type = ReceiptElementType.DYNAMIC_FIELD,
                fieldTag = "{JOB_NO}",
                customLabel = "JOB ID: ",
                fontSize = ReceiptTextSize.NORMAL,
                isBold = true,
                alignment = ReceiptAlignment.LEFT
            ),
            ReceiptElement(
                type = ReceiptElementType.DIVIDER,
                dividerStyle = ReceiptDividerStyle.SOLID,
                spacingBottom = 5
            ),
            ReceiptElement(
                type = ReceiptElementType.DYNAMIC_FIELD,
                fieldTag = "{TOTAL}",
                customLabel = "NET PAYABLE: ",
                fontSize = ReceiptTextSize.LARGE,
                isBold = true,
                alignment = ReceiptAlignment.RIGHT
            ),
            ReceiptElement(
                type = ReceiptElementType.BLANK_SPACE,
                spaceHeight = 15
            ),
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                text = "SPECIAL CUSTOM FOOTER\nWarranty 90 Days",
                fontSize = ReceiptTextSize.SMALL,
                isBold = false,
                alignment = ReceiptAlignment.CENTER,
                spacingBottom = 10
            )
        )
        val customCustomerTemplate = ReceiptTemplate("CUSTOMER", customCustomerElements)
        ReceiptPreferences.saveCustomerTemplate(app, customCustomerTemplate)

        val customPaymentElements = listOf(
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                text = "UDM OFFICIAL PAYMENT VOUCHER",
                fontSize = ReceiptTextSize.LARGE,
                isBold = true,
                alignment = ReceiptAlignment.CENTER,
                spacingBottom = 8
            ),
            ReceiptElement(
                type = ReceiptElementType.DYNAMIC_FIELD,
                fieldTag = "{PAYMENT_AMOUNT}",
                customLabel = "AMOUNT SETTLED: ",
                fontSize = ReceiptTextSize.LARGE,
                isBold = true,
                alignment = ReceiptAlignment.CENTER
            ),
            ReceiptElement(
                type = ReceiptElementType.DIVIDER,
                dividerStyle = ReceiptDividerStyle.DASHED,
                spacingBottom = 4
            ),
            ReceiptElement(
                type = ReceiptElementType.TEXT,
                text = "PAYMENT RECEIVED WITH THANKS",
                fontSize = ReceiptTextSize.NORMAL,
                isBold = true,
                alignment = ReceiptAlignment.CENTER,
                spacingBottom = 6
            )
        )
        val customPaymentTemplate = ReceiptTemplate("PAYMENT", customPaymentElements)
        ReceiptPreferences.savePaymentTemplate(app, customPaymentTemplate)

        // Customize bridge configs and printer settings
        val customCustomerConfig = CustomerReceiptConfig(
            shopName = "Custom Express Repair",
            subtitle = "Authorized Specialist",
            phone = "0778899000",
            address = "No. 99, Galle Road",
            whatsapp = "+94778899000",
            footer = "Thank you - Express Repair",
            textSize = ReceiptTextSize.LARGE,
            headerAlignment = ReceiptAlignment.CENTER,
            bodyAlignment = ReceiptAlignment.LEFT,
            footerAlignment = ReceiptAlignment.CENTER,
            dividerStyle = ReceiptDividerStyle.SOLID
        )
        ReceiptPreferences.saveCustomerReceiptConfig(app, customCustomerConfig)

        val customPaymentConfig = PaymentReceiptConfig(
            shopName = "Custom Express Repair",
            receiptTitle = "OFFICIAL RECEIPT",
            footer = "Accounts Dept",
            textSize = ReceiptTextSize.NORMAL,
            headerAlignment = ReceiptAlignment.RIGHT,
            dividerStyle = ReceiptDividerStyle.DASHED
        )
        ReceiptPreferences.savePaymentReceiptConfig(app, customPaymentConfig)

        PrinterPreferences.setAutoPrintEnabled(app, true)
        PrinterPreferences.setCustomerReceiptDiagnosticMode(app, true)
        val customP50s = P50SPrintingSystemSettings(
            imagePacketPacingMs = 35L,
            rasterMaxHeight = 250,
            postRasterDelayMs = 450L
        )
        PrinterPreferences.saveP50SSettings(app, customP50s)

        // Step D: Export a JSON backup
        val backupData = repository.getAllDataForBackup()
        assertNotNull("BackupData must contain receiptCustomization", backupData.receiptCustomization)
        val json = BackupManager.exportToJson(backupData)

        // Verify JSON contents
        assertTrue(json.contains("\"receiptCustomization\":"))
        assertTrue(json.contains("SPECIAL CUSTOM HEADER"))
        assertTrue(json.contains("SPECIAL CUSTOM FOOTER"))
        assertTrue(json.contains("UDM OFFICIAL PAYMENT VOUCHER"))
        assertTrue(json.contains("test_shop_logo.png"))
        assertTrue(json.contains("NET PAYABLE:"))

        // Validate JSON summary
        val validation = BackupManager.validateJson(json)
        assertTrue("Backup validation must succeed", validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid
        assertTrue("Summary must flag receipt customization presence", valid.summary.hasReceiptCustomization)

        // Step E: Change the receipt designs in the app (e.g. reset or modify)
        ReceiptPreferences.resetCustomerTemplate(app)
        ReceiptPreferences.resetPaymentTemplate(app)
        ReceiptPreferences.resetCustomerReceiptConfig(app)
        ReceiptPreferences.resetPaymentReceiptConfig(app)
        PrinterPreferences.setAutoPrintEnabled(app, false)
        PrinterPreferences.setCustomerReceiptDiagnosticMode(app, false)
        PrinterPreferences.resetP50SSettings(app)

        // Delete the original logo file to verify restoration from Base64 backup
        sampleLogo.delete()
        assertFalse("Original logo file must be deleted to simulate fresh restore", sampleLogo.exists())

        // Verify that current settings are altered
        val modifiedCust = ReceiptPreferences.getCustomerTemplate(app)
        assertFalse(modifiedCust.elements.any { it.text == "SPECIAL CUSTOM HEADER" })
        assertEquals(false, PrinterPreferences.isAutoPrintEnabled(app))

        // Step F: Restore the backup
        repository.restoreData(valid.data, clearExisting = false)

        // Expected: Both receipt designs and all backed-up settings return to their previous configurations!
        val restoredCust = ReceiptPreferences.getCustomerTemplate(app)
        assertEquals("CUSTOMER", restoredCust.templateType)
        val restoredHeader = restoredCust.elements.find { it.text == "SPECIAL CUSTOM HEADER" }
        assertNotNull("Restored customer template must have custom header", restoredHeader)
        assertEquals(ReceiptTextSize.LARGE, restoredHeader!!.fontSize)
        assertEquals(ReceiptAlignment.CENTER, restoredHeader.alignment)

        val restoredFooter = restoredCust.elements.find { it.text.contains("SPECIAL CUSTOM FOOTER") }
        assertNotNull("Restored customer template must have custom footer", restoredFooter)

        val restoredJobLabel = restoredCust.elements.find { it.fieldTag == "{JOB_NO}" }
        assertEquals("JOB ID: ", restoredJobLabel?.customLabel)

        val restoredTotalLabel = restoredCust.elements.find { it.fieldTag == "{TOTAL}" }
        assertEquals("NET PAYABLE: ", restoredTotalLabel?.customLabel)
        assertEquals(ReceiptAlignment.RIGHT, restoredTotalLabel?.alignment)

        // Verify custom logo was restored to disk and element logoPath was mapped
        val restoredLogoEl = restoredCust.elements.find { it.type == ReceiptElementType.IMAGE_LOGO }
        assertNotNull("Restored customer template must contain logo element", restoredLogoEl)
        assertTrue("Restored logoPath must not be blank", restoredLogoEl!!.logoPath.isNotBlank())
        val restoredLogoFile = File(restoredLogoEl.logoPath)
        assertTrue("Restored logo file must physically exist on disk", restoredLogoFile.exists())
        assertTrue("Restored logo file must have non-zero size", restoredLogoFile.length() > 0)
        assertEquals(ReceiptLogoSize.LARGE, restoredLogoEl.logoSize)

        // Verify Payment template restored
        val restoredPay = ReceiptPreferences.getPaymentTemplate(app)
        assertEquals("PAYMENT", restoredPay.templateType)
        val payTitle = restoredPay.elements.find { it.text == "UDM OFFICIAL PAYMENT VOUCHER" }
        assertNotNull("Restored payment template must contain custom title", payTitle)

        // Verify legacy configs restored
        val restoredCustCfg = ReceiptPreferences.getCustomerReceiptConfig(app)
        assertEquals("Custom Express Repair", restoredCustCfg.shopName)
        assertEquals("0778899000", restoredCustCfg.phone)
        assertEquals(ReceiptDividerStyle.SOLID, restoredCustCfg.dividerStyle)

        val restoredPayCfg = ReceiptPreferences.getPaymentReceiptConfig(app)
        assertEquals("OFFICIAL RECEIPT", restoredPayCfg.receiptTitle)
        assertEquals(ReceiptAlignment.RIGHT, restoredPayCfg.headerAlignment)

        // Verify printer settings restored
        assertTrue(PrinterPreferences.isAutoPrintEnabled(app))
        assertTrue(PrinterPreferences.isCustomerReceiptDiagnosticMode(app))
        val restoredP50s = PrinterPreferences.getP50SSettings(app)
        assertEquals(35L, restoredP50s.imagePacketPacingMs)
        assertEquals(250, restoredP50s.rasterMaxHeight)
        assertEquals(450L, restoredP50s.postRasterDelayMs)
    }

    /**
     * Acceptance Test G:
     * Restart the app and print or preview both receipt types.
     * Expected: The restored designs remain applied.
     */
    @Test
    fun `test Acceptance Criteria G - simulated app restart preserves restored designs and previews work`() = runBlocking {
        // Setup customized templates in storage
        val customCustomerElements = listOf(
            ReceiptElement(type = ReceiptElementType.TEXT, text = "RESTART PERSISTENCE TEST HEADER", fontSize = ReceiptTextSize.LARGE, isBold = true, alignment = ReceiptAlignment.CENTER),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{TOTAL}", customLabel = "GRAND TOTAL: ", fontSize = ReceiptTextSize.LARGE, isBold = true, alignment = ReceiptAlignment.RIGHT)
        )
        val custTmpl = ReceiptTemplate("CUSTOMER", customCustomerElements)
        ReceiptPreferences.saveCustomerTemplate(app, custTmpl)

        val customPaymentElements = listOf(
            ReceiptElement(type = ReceiptElementType.TEXT, text = "RESTART PERSISTENCE PAYMENT HEADER", fontSize = ReceiptTextSize.LARGE, isBold = true, alignment = ReceiptAlignment.CENTER)
        )
        val payTmpl = ReceiptTemplate("PAYMENT", customPaymentElements)
        ReceiptPreferences.savePaymentTemplate(app, payTmpl)

        // Simulate app restart by re-reading directly from ReceiptPreferences
        val reloadedCust = ReceiptPreferences.getCustomerTemplate(app)
        val reloadedPay = ReceiptPreferences.getPaymentTemplate(app)

        assertEquals("CUSTOMER", reloadedCust.templateType)
        assertEquals("RESTART PERSISTENCE TEST HEADER", reloadedCust.elements[0].text)
        assertEquals("RESTART PERSISTENCE PAYMENT HEADER", reloadedPay.elements[0].text)

        // Generate previews
        val customerPreview = ReceiptBitmapGenerator.generateCustomerReceiptPreview(reloadedCust)
        assertNotNull("Customer receipt preview bitmap must not be null", customerPreview)
        assertEquals(384, customerPreview.width)
        assertTrue(customerPreview.height > 50)

        val paymentPreview = ReceiptBitmapGenerator.generatePaymentReceiptPreview(reloadedPay)
        assertNotNull("Payment receipt preview bitmap must not be null", paymentPreview)
        assertEquals(384, paymentPreview.width)
        assertTrue(paymentPreview.height > 50)
    }

    /**
     * Acceptance Test H:
     * Restore an older backup that has no receipt design data.
     * Expected: Restore succeeds without corrupting existing data or unexpectedly resetting current receipt designs.
     */
    @Test
    fun `test Acceptance Criteria H - restoring older backup without receipt design data preserves existing designs`() = runBlocking {
        // First, configure a custom receipt design
        val preExistingElements = listOf(
            ReceiptElement(type = ReceiptElementType.TEXT, text = "MY PRE-EXISTING CUSTOM DESIGN", fontSize = ReceiptTextSize.LARGE, isBold = true)
        )
        ReceiptPreferences.saveCustomerTemplate(app, ReceiptTemplate("CUSTOMER", preExistingElements))
        ReceiptPreferences.saveCustomerReceiptConfig(app, CustomerReceiptConfig(shopName = "Pre-Existing Shop Name"))

        // Create an older backup JSON string without any receiptCustomization field
        val olderBackupJson = """
            {
              "app": "UDM MOBILE REPAIR",
              "version": 1,
              "exportedAt": 1700000000000,
              "repairs": [
                {
                  "id": 101,
                  "jobNumber": "0099",
                  "customerName": "Nimal Sirisena",
                  "customerPhone": "0775551234",
                  "brand": "Nokia",
                  "model": "3310",
                  "imei": "112233445566778",
                  "fault": "Keypad not working",
                  "totalPrice": 1200.0,
                  "amountPaid": 1200.0,
                  "balance": 0.0,
                  "paymentStatus": "PAID",
                  "status": "DELIVERED",
                  "receivedDate": "2026-05-10",
                  "receivedTime": "10:30"
                }
              ],
              "repairItems": [
                {
                  "id": 201,
                  "repairId": 101,
                  "repairType": "Keypad Repair",
                  "price": 1200.0
                }
              ],
              "payments": [],
              "statusHistory": [],
              "customers": [],
              "brands": [],
              "models": [],
              "faults": [],
              "repairTypes": [],
              "prices": [],
              "settings": {
                "shopName": "UDM MOBILE REPAIR",
                "shopPhone": "+94 77 123 4567"
              }
            }
        """.trimIndent()

        // Validate the older backup format
        val validation = BackupManager.validateJson(olderBackupJson)
        assertTrue("Older backup must validate successfully", validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid
        assertFalse("Older backup must not have receipt customization", valid.summary.hasReceiptCustomization)
        assertEquals(1, valid.summary.repairCount)

        // Restore older backup
        repository.restoreData(valid.data, clearExisting = false)

        // 1. Verify older repair data was restored
        val restoredRepair = repository.getRepairByJobNumber("0099")
        assertNotNull("Older repair must be restored into database", restoredRepair)
        assertEquals("Nimal Sirisena", restoredRepair!!.customerName)
        assertEquals(1200.0, restoredRepair.totalPrice, 0.01)

        // 2. Expected: Current receipt designs remain unchanged and NOT reset!
        val currentCustTmpl = ReceiptPreferences.getCustomerTemplate(app)
        val designText = currentCustTmpl.elements.find { it.text == "MY PRE-EXISTING CUSTOM DESIGN" }
        assertNotNull("Current custom receipt design must remain intact when restoring older backup", designText)

        val currentCustCfg = ReceiptPreferences.getCustomerReceiptConfig(app)
        assertEquals("Pre-Existing Shop Name", currentCustCfg.shopName)
    }

    /**
     * Test saving backup to file and reading back preserves receipt customization
     */
    @Test
    fun `test BackupFileHelper save and discover preserves receipt customization metadata`() = runBlocking {
        // Setup customization
        ReceiptPreferences.saveCustomerTemplate(
            app,
            ReceiptTemplate("CUSTOMER", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "FILE BACKUP TEST")))
        )

        val data = repository.getAllDataForBackup()
        val json = BackupManager.exportToJson(data)

        val saveResult = BackupFileHelper.saveBackupToFile(app, json)
        assertTrue("Backup file save must succeed", saveResult.success)
        assertNotNull(saveResult.summary)
        assertTrue("Summary must show receipt customization included", saveResult.summary!!.hasReceiptCustomization)

        // Read file back and validate
        val content = saveResult.file!!.readText(Charsets.UTF_8)
        val validation = BackupManager.validateJson(content)
        assertTrue(validation is BackupValidationResult.Valid)
        assertTrue((validation as BackupValidationResult.Valid).summary.hasReceiptCustomization)
    }
}
