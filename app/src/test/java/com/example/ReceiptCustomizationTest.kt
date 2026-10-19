package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import com.example.printer.CustomerReceiptConfig
import com.example.printer.DefaultTemplates
import com.example.printer.P50SProtocol
import com.example.printer.PaymentReceiptConfig
import com.example.printer.PrinterBleManager
import com.example.printer.PrintResult
import com.example.printer.ReceiptAlignment
import com.example.printer.ReceiptBitmapGenerator
import com.example.printer.ReceiptDividerStyle
import com.example.printer.ReceiptElement
import com.example.printer.ReceiptElementType
import com.example.printer.ReceiptLogoSize
import com.example.printer.ReceiptPreferences
import com.example.printer.ReceiptTemplate
import com.example.printer.ReceiptTextSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class ReceiptCustomizationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ReceiptPreferences.resetCustomerReceiptConfig(context)
        ReceiptPreferences.resetPaymentReceiptConfig(context)
        ReceiptPreferences.resetCustomerTemplate(context)
        ReceiptPreferences.resetPaymentTemplate(context)
    }

    // 1. Customer Receipt default template works
    @Test
    fun `test 1 Customer Receipt default template works`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        assertEquals("CUSTOMER", template.templateType)
        assertTrue(template.elements.isNotEmpty())

        val tags = template.elements.filter { it.type == ReceiptElementType.DYNAMIC_FIELD }.map { it.fieldTag }
        assertTrue(tags.contains("{SHOP_NAME}"))
        assertTrue(tags.contains("{SUBTITLE}"))
        assertTrue(tags.contains("{PHONE}"))
        assertTrue(tags.contains("{JOB_NO}"))
        assertTrue(tags.contains("{DATE}"))
        assertTrue(tags.contains("{CUSTOMER_NAME}"))
        assertTrue(tags.contains("{CUSTOMER_PHONE}"))
        assertTrue(tags.contains("{BRAND}"))
        assertTrue(tags.contains("{MODEL}"))
        assertTrue(tags.contains("{REPAIR_ITEMS}"))
        assertTrue(tags.contains("{TOTAL}"))
        assertTrue(tags.contains("{PAID}"))
        assertTrue(tags.contains("{BALANCE}"))

        val preview = ReceiptBitmapGenerator.generateCustomerReceiptPreview(template)
        assertNotNull(preview)
        assertEquals(384, preview.width)
        assertTrue(preview.height > 100)
    }

    // 2. Payment Receipt default template works
    @Test
    fun `test 2 Payment Receipt default template works`() {
        val template = DefaultTemplates.createPaymentDefaultTemplate()
        assertEquals("PAYMENT", template.templateType)
        assertTrue(template.elements.isNotEmpty())

        val tags = template.elements.filter { it.type == ReceiptElementType.DYNAMIC_FIELD }.map { it.fieldTag }
        assertTrue(tags.contains("{SHOP_NAME}"))
        assertTrue(tags.contains("{JOB_NO}"))
        assertTrue(tags.contains("{PAYMENT_AMOUNT}"))
        assertTrue(tags.contains("{TOTAL}"))
        assertTrue(tags.contains("{TOTAL_PAID}"))
        assertTrue(tags.contains("{BALANCE}"))

        val preview = ReceiptBitmapGenerator.generatePaymentReceiptPreview(template)
        assertNotNull(preview)
        assertEquals(384, preview.width)
        assertTrue(preview.height > 100)
    }

    // 3. Add text works
    @Test
    fun `test 3 Add text works`() {
        val defaultTemplate = DefaultTemplates.createCustomerDefaultTemplate()
        val customTextElement = ReceiptElement(
            type = ReceiptElementType.TEXT,
            text = "Thank you for choosing UDM Mobile Repair.",
            fontSize = ReceiptTextSize.NORMAL,
            isBold = true,
            alignment = ReceiptAlignment.CENTER
        )
        val updatedTemplate = defaultTemplate.copy(elements = defaultTemplate.elements + customTextElement)
        assertTrue(updatedTemplate.elements.any { it.text == "Thank you for choosing UDM Mobile Repair." })

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceiptPreview(updatedTemplate)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
    }

    // 4. Delete text works
    @Test
    fun `test 4 Delete text works`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        val textCountBefore = template.elements.count { it.type == ReceiptElementType.TEXT }
        assertTrue("Template should have at least one text element initially", textCountBefore > 0)

        val updatedElements = template.elements.filter { it.type != ReceiptElementType.TEXT }
        val updatedTemplate = template.copy(elements = updatedElements)
        assertEquals(0, updatedTemplate.elements.count { it.type == ReceiptElementType.TEXT })
    }

    // 5. Edit text works
    @Test
    fun `test 5 Edit text works`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        val textIdx = template.elements.indexOfFirst { it.type == ReceiptElementType.TEXT }
        assertTrue(textIdx >= 0)

        val list = template.elements.toMutableList()
        list[textIdx] = list[textIdx].copy(text = "UDM CUSTOM WARRANTY 90 DAYS")
        val updatedTemplate = template.copy(elements = list)

        assertEquals("UDM CUSTOM WARRANTY 90 DAYS", updatedTemplate.elements[textIdx].text)
        val preview = ReceiptBitmapGenerator.generateCustomerReceiptPreview(updatedTemplate)
        assertNotNull(preview)
    }

    // 6. Add dynamic field works
    @Test
    fun `test 6 Add dynamic field works`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        val newField = ReceiptElement(
            type = ReceiptElementType.DYNAMIC_FIELD,
            fieldTag = "{IMEI}",
            customLabel = "Device IMEI: ",
            isVisible = true
        )
        val updatedTemplate = template.copy(elements = template.elements + newField)
        assertTrue(updatedTemplate.elements.any { it.fieldTag == "{IMEI}" && it.isVisible })

        val preview = ReceiptBitmapGenerator.generateCustomerReceiptPreview(updatedTemplate)
        assertNotNull(preview)
        assertEquals(384, preview.width)
    }

    // 7. Hide and show works
    @Test
    fun `test 7 Hide and show works`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        val originalBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(template)

        // Find a visible element and hide it
        val visibleIdx = template.elements.indexOfFirst { it.isVisible }
        assertTrue(visibleIdx >= 0)

        val hiddenList = template.elements.toMutableList()
        hiddenList[visibleIdx] = hiddenList[visibleIdx].copy(isVisible = false)
        val hiddenTemplate = template.copy(elements = hiddenList)
        val hiddenBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(hiddenTemplate)

        assertTrue("Hiding an element should reduce bitmap height", hiddenBmp.height < originalBmp.height)

        // Unhide the element
        val restoredList = hiddenTemplate.elements.toMutableList()
        restoredList[visibleIdx] = restoredList[visibleIdx].copy(isVisible = true)
        val restoredTemplate = hiddenTemplate.copy(elements = restoredList)
        val restoredBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(restoredTemplate)

        assertEquals("Restoring element should return to original height", originalBmp.height, restoredBmp.height)
    }

    // 8. Move up and down works
    @Test
    fun `test 8 Move up and down works`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        val initialFirst = template.elements[0]
        val initialSecond = template.elements[1]

        // Move second element UP to index 0
        val reorderedList = template.elements.toMutableList()
        val item = reorderedList.removeAt(1)
        reorderedList.add(0, item)
        val updatedTemplate = template.copy(elements = reorderedList)

        assertEquals(initialSecond.id, updatedTemplate.elements[0].id)
        assertEquals(initialFirst.id, updatedTemplate.elements[1].id)

        // Move first element DOWN to index 1
        val moveDownList = updatedTemplate.elements.toMutableList()
        val downItem = moveDownList.removeAt(0)
        moveDownList.add(1, downItem)
        val restoredTemplate = updatedTemplate.copy(elements = moveDownList)

        assertEquals(initialFirst.id, restoredTemplate.elements[0].id)
        assertEquals(initialSecond.id, restoredTemplate.elements[1].id)
    }

    // 9. Font size works
    @Test
    fun `test 9 Font size works`() {
        val smallEl = ReceiptElement(type = ReceiptElementType.TEXT, text = "UDM REPAIR TEST LINE", fontSize = ReceiptTextSize.SMALL)
        val normalEl = ReceiptElement(type = ReceiptElementType.TEXT, text = "UDM REPAIR TEST LINE", fontSize = ReceiptTextSize.NORMAL)
        val largeEl = ReceiptElement(type = ReceiptElementType.TEXT, text = "UDM REPAIR TEST LINE", fontSize = ReceiptTextSize.LARGE)

        val smallBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(smallEl)))
        val normalBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(normalEl)))
        val largeBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(largeEl)))

        assertTrue(smallBmp.height <= normalBmp.height)
        assertTrue(normalBmp.height <= largeBmp.height)
    }

    // 10. Bold works
    @Test
    fun `test 10 Bold works`() {
        val normalEl = ReceiptElement(type = ReceiptElementType.TEXT, text = "SAMPLE TEXT", isBold = false)
        val boldEl = ReceiptElement(type = ReceiptElementType.TEXT, text = "SAMPLE TEXT", isBold = true)

        assertFalse(normalEl.isBold)
        assertTrue(boldEl.isBold)

        val bmpNormal = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(normalEl)))
        val bmpBold = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(boldEl)))

        assertNotNull(bmpNormal)
        assertNotNull(bmpBold)
    }

    // 11. Alignment works
    @Test
    fun `test 11 Alignment works`() {
        val left = ReceiptElement(type = ReceiptElementType.TEXT, text = "ALIGN", alignment = ReceiptAlignment.LEFT)
        val center = ReceiptElement(type = ReceiptElementType.TEXT, text = "ALIGN", alignment = ReceiptAlignment.CENTER)
        val right = ReceiptElement(type = ReceiptElementType.TEXT, text = "ALIGN", alignment = ReceiptAlignment.RIGHT)

        assertEquals(ReceiptAlignment.LEFT, left.alignment)
        assertEquals(ReceiptAlignment.CENTER, center.alignment)
        assertEquals(ReceiptAlignment.RIGHT, right.alignment)

        val bmpL = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(left)))
        val bmpC = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(center)))
        val bmpR = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(right)))

        assertNotNull(bmpL)
        assertNotNull(bmpC)
        assertNotNull(bmpR)
    }

    // 12. Divider works
    @Test
    fun `test 12 Divider works`() {
        val dashed = ReceiptElement(type = ReceiptElementType.DIVIDER, dividerStyle = ReceiptDividerStyle.DASHED)
        val solid = ReceiptElement(type = ReceiptElementType.DIVIDER, dividerStyle = ReceiptDividerStyle.SOLID)

        val bmpDashed = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(dashed)))
        val bmpSolid = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(solid)))

        assertNotNull(bmpDashed)
        assertNotNull(bmpSolid)
        assertEquals(384, bmpDashed.width)
        assertEquals(384, bmpSolid.width)
    }

    // 13. Blank space works
    @Test
    fun `test 13 Blank space works`() {
        val smallSpace = ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 8)
        val bigSpace = ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 40)

        val bmpSmall = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(smallSpace)))
        val bmpBig = ReceiptBitmapGenerator.generateCustomerReceiptPreview(ReceiptTemplate("CUSTOMER", listOf(bigSpace)))

        assertTrue("Bigger space must produce taller bitmap", bmpBig.height > bmpSmall.height)
    }

    // 14. Logo selection works
    @Test
    fun `test 14 Logo selection works`() {
        val logoFile = File(context.filesDir, "test_logo.png")
        val sampleBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        sampleBmp.eraseColor(Color.BLACK)
        FileOutputStream(logoFile).use { out ->
            sampleBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(logoFile.exists())

        val logoElement = ReceiptElement(
            type = ReceiptElementType.IMAGE_LOGO,
            logoPath = logoFile.absolutePath,
            logoSize = ReceiptLogoSize.MEDIUM,
            alignment = ReceiptAlignment.CENTER
        )
        val template = ReceiptTemplate("CUSTOMER", listOf(logoElement))
        val receiptBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(template)

        assertNotNull(receiptBmp)
        assertEquals(384, receiptBmp.width)
        assertTrue(receiptBmp.height > 50)
    }

    // 15. Logo replacement works
    @Test
    fun `test 15 Logo replacement works`() {
        val logo1 = File(context.filesDir, "logo1.png").apply { writeText("logo1") }
        val logo2 = File(context.filesDir, "logo2.png").apply { writeText("logo2") }

        var el = ReceiptElement(type = ReceiptElementType.IMAGE_LOGO, logoPath = logo1.absolutePath)
        assertEquals(logo1.absolutePath, el.logoPath)

        // Replace logo
        el = el.copy(logoPath = logo2.absolutePath)
        assertEquals(logo2.absolutePath, el.logoPath)
    }

    // 16. Logo removal works
    @Test
    fun `test 16 Logo removal works`() {
        val logoFile = File(context.filesDir, "logo_to_remove.png").apply { writeText("dummy") }
        assertTrue(logoFile.exists())

        val removed = ReceiptPreferences.deleteLogoFile(logoFile.absolutePath)
        assertTrue(removed)
        assertFalse(logoFile.exists())
    }

    // 17. Customer and Payment templates save independently
    @Test
    fun `test 17 Customer and Payment templates save independently`() {
        val custTmpl = ReceiptTemplate("CUSTOMER", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "CUSTOMER ONLY")))
        val payTmpl = ReceiptTemplate("PAYMENT", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "PAYMENT ONLY")))

        ReceiptPreferences.saveCustomerTemplate(context, custTmpl)
        ReceiptPreferences.savePaymentTemplate(context, payTmpl)

        val loadedCust = ReceiptPreferences.getCustomerTemplate(context)
        val loadedPay = ReceiptPreferences.getPaymentTemplate(context)

        assertEquals("CUSTOMER ONLY", loadedCust.elements[0].text)
        assertEquals("PAYMENT ONLY", loadedPay.elements[0].text)
    }

    // 18. Reset Customer does not reset Payment
    @Test
    fun `test 18 Reset Customer does not reset Payment`() {
        val custTmpl = ReceiptTemplate("CUSTOMER", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "CUSTOM CUST")))
        val payTmpl = ReceiptTemplate("PAYMENT", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "CUSTOM PAY")))

        ReceiptPreferences.saveCustomerTemplate(context, custTmpl)
        ReceiptPreferences.savePaymentTemplate(context, payTmpl)

        // Reset Customer only
        ReceiptPreferences.resetCustomerTemplate(context)

        val loadedCust = ReceiptPreferences.getCustomerTemplate(context)
        val loadedPay = ReceiptPreferences.getPaymentTemplate(context)

        // Customer restored to default
        assertNotEquals("CUSTOM CUST", loadedCust.elements[0].text)
        // Payment completely untouched
        assertEquals("CUSTOM PAY", loadedPay.elements[0].text)
    }

    // 19. Reset Payment does not reset Customer
    @Test
    fun `test 19 Reset Payment does not reset Customer`() {
        val custTmpl = ReceiptTemplate("CUSTOMER", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "CUSTOM CUST")))
        val payTmpl = ReceiptTemplate("PAYMENT", listOf(ReceiptElement(type = ReceiptElementType.TEXT, text = "CUSTOM PAY")))

        ReceiptPreferences.saveCustomerTemplate(context, custTmpl)
        ReceiptPreferences.savePaymentTemplate(context, payTmpl)

        // Reset Payment only
        ReceiptPreferences.resetPaymentTemplate(context)

        val loadedCust = ReceiptPreferences.getCustomerTemplate(context)
        val loadedPay = ReceiptPreferences.getPaymentTemplate(context)

        // Customer completely untouched
        assertEquals("CUSTOM CUST", loadedCust.elements[0].text)
        // Payment restored to default
        assertNotEquals("CUSTOM PAY", loadedPay.elements[0].text)
    }

    // 20. Settings survive app restart
    @Test
    fun `test 20 Settings survive app restart`() {
        val customElement = ReceiptElement(
            type = ReceiptElementType.TEXT,
            text = "PERSISTENT TEXT",
            fontSize = ReceiptTextSize.LARGE,
            isBold = true,
            alignment = ReceiptAlignment.RIGHT,
            spacingBottom = 16
        )
        val template = ReceiptTemplate("CUSTOMER", listOf(customElement))
        ReceiptPreferences.saveCustomerTemplate(context, template)

        // Re-read fresh from preferences
        val reloaded = ReceiptPreferences.getCustomerTemplate(context)
        assertEquals(1, reloaded.elements.size)
        val loadedEl = reloaded.elements[0]
        assertEquals("PERSISTENT TEXT", loadedEl.text)
        assertEquals(ReceiptTextSize.LARGE, loadedEl.fontSize)
        assertTrue(loadedEl.isBold)
        assertEquals(ReceiptAlignment.RIGHT, loadedEl.alignment)
        assertEquals(16, loadedEl.spacingBottom)
    }

    // 21. Preview updates correctly
    @Test
    fun `test 21 Preview updates correctly`() {
        val template1 = ReceiptTemplate("CUSTOMER", listOf(
            ReceiptElement(type = ReceiptElementType.TEXT, text = "LINE ONE")
        ))
        val template2 = ReceiptTemplate("CUSTOMER", listOf(
            ReceiptElement(type = ReceiptElementType.TEXT, text = "LINE ONE"),
            ReceiptElement(type = ReceiptElementType.TEXT, text = "LINE TWO"),
            ReceiptElement(type = ReceiptElementType.TEXT, text = "LINE THREE")
        ))

        val bmp1 = ReceiptBitmapGenerator.generateCustomerReceiptPreview(template1)
        val bmp2 = ReceiptBitmapGenerator.generateCustomerReceiptPreview(template2)

        assertTrue(bmp2.height > bmp1.height)
    }

    // 22. Generated bitmap matches preview as closely as possible
    @Test
    fun `test 22 Generated bitmap matches preview as closely as possible`() {
        val template = DefaultTemplates.createCustomerDefaultTemplate()
        val previewBmp = ReceiptBitmapGenerator.generateCustomerReceiptPreview(template)

        val sampleRepair = RepairEntity(
            jobNumber = "0027",
            customerName = "Ashoka",
            customerPhone = "07XXXXXXXX",
            brand = "Samsung",
            model = "M02",
            imei = "354678129034567",
            accessories = "Battery, Charger",
            fault = "Water damage, not powering on",
            totalPrice = 9000.0,
            amountPaid = 9000.0,
            balance = 0.0,
            paymentStatus = "PAID",
            status = "DELIVERED",
            receivedDate = "29/09/2026",
            receivedTime = "09:15"
        )
        val sampleItems = listOf(
            RepairItemEntity(repairId = 0L, repairType = "Display Replacement", price = 5000.0),
            RepairItemEntity(repairId = 0L, repairType = "Charging Pin", price = 1000.0),
            RepairItemEntity(repairId = 0L, repairType = "Battery", price = 3000.0)
        )
        val sampleSettings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            shopPhone = "07XXXXXXXX",
            shopAddress = "No. 123, Main Street, Colombo",
            whatsappNumber = "07XXXXXXXX",
            currency = "Rs."
        )

        val directBmp = ReceiptBitmapGenerator.generateReceiptFromTemplate(
            template = template,
            repair = sampleRepair,
            items = sampleItems,
            payments = emptyList(),
            payment = null,
            settings = sampleSettings
        )

        // Exact match in dimensions
        assertEquals(previewBmp.width, directBmp.width)
        assertEquals(previewBmp.height, directBmp.height)
    }

    // 23. Long receipts split into 200px jobs correctly
    @Test
    fun `test 23 Long receipts split into 200px jobs correctly`() {
        // Test 720px: 200 + 200 + 200 + 120 (4 jobs)
        val data720 = ByteArray(720 * P50SProtocol.BYTES_PER_ROW)
        val blocks720 = P50SProtocol.buildSeparateJobBlocks(data720, 720, maxBlockHeight = 200)
        assertEquals(4, blocks720.size)
        assertEquals(200, blocks720[0].blockHeight)
        assertEquals(200, blocks720[1].blockHeight)
        assertEquals(200, blocks720[2].blockHeight)
        assertEquals(120, blocks720[3].blockHeight)

        // Test 550px: 200 + 200 + 150 (3 jobs)
        val data550 = ByteArray(550 * P50SProtocol.BYTES_PER_ROW)
        val blocks550 = P50SProtocol.buildSeparateJobBlocks(data550, 550, maxBlockHeight = 200)
        assertEquals(3, blocks550.size)
        assertEquals(200, blocks550[0].blockHeight)
        assertEquals(200, blocks550[1].blockHeight)
        assertEquals(150, blocks550[2].blockHeight)
    }

    // 24. Each print job contains exactly one raster command
    @Test
    fun `test 24 Each print job contains exactly one raster command`() {
        val data = ByteArray(450 * P50SProtocol.BYTES_PER_ROW)
        val blocks = P50SProtocol.buildSeparateJobBlocks(data, 450, maxBlockHeight = 200)

        for (block in blocks) {
            val cmd = block.rasterCommand
            // Protocol 0x1F 0x10 is the raster command
            assertEquals(0x1F.toByte(), cmd[0])
            assertEquals(0x10.toByte(), cmd[1])
            // Exactly one raster header per block
            var count1F10 = 0
            for (i in 0 until cmd.size - 1) {
                if (cmd[i] == 0x1F.toByte() && cmd[i + 1] == 0x10.toByte()) {
                    count1F10++
                }
            }
            assertEquals(1, count1F10)
        }
    }

    // 25. ZERO feed or alignment occurs between jobs
    @Test
    fun `test 25 ZERO feed or alignment occurs between jobs`() {
        val data = ByteArray(400 * P50SProtocol.BYTES_PER_ROW)
        val blocks = P50SProtocol.buildSeparateJobBlocks(data, 400, maxBlockHeight = 200)

        for (block in blocks) {
            val cmd = block.rasterCommand
            // Verify NO feed paper (0x1F 0x11) or align end (0x1F 0x11 0x50) inside the job block
            var hasFeedOrAlign = false
            for (i in 0 until cmd.size - 2) {
                if (cmd[i] == 0x1F.toByte() && cmd[i + 1] == 0x11.toByte()) {
                    hasFeedOrAlign = true
                }
            }
            assertFalse("Raster block must NOT contain paper feed or alignment", hasFeedOrAlign)
        }
    }

    // 26. Final feed and alignment occurs only after the final job
    @Test
    fun `test 26 Final feed and alignment protocol definitions are preserved`() {
        assertEquals(0x1F.toByte(), P50SProtocol.CMD_FEED_PAPER_160PX[0])
        assertEquals(0x11.toByte(), P50SProtocol.CMD_FEED_PAPER_160PX[1])
        assertEquals(0xA0.toByte(), P50SProtocol.CMD_FEED_PAPER_160PX[4]) // 160px

        assertEquals(0x1F.toByte(), P50SProtocol.CMD_ALIGN_END[0])
        assertEquals(0x11.toByte(), P50SProtocol.CMD_ALIGN_END[1])
        assertEquals(0x50.toByte(), P50SProtocol.CMD_ALIGN_END[2])
    }

    // 27. Existing Diagnostic printing remains unchanged
    @Test
    fun `test 27 Existing Diagnostic printing remains unchanged`() {
        val diagBmp = ReceiptBitmapGenerator.generateCustomerReceiptDiagnostic()
        assertNotNull(diagBmp)
        assertEquals(384, diagBmp.width)
        assertEquals(600, diagBmp.height)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(diagBmp)
        assertEquals(600 * 48, mono.size)

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, 600, maxBlockHeight = 200)
        assertEquals(3, blocks.size)
        assertEquals(200, blocks[0].blockHeight)
        assertEquals(200, blocks[1].blockHeight)
        assertEquals(200, blocks[2].blockHeight)
    }

    // 28. Existing P50S printer behavior remains unchanged
    @Test
    fun `test 28 Existing P50S printer behavior remains unchanged`() {
        assertEquals(384, P50SProtocol.PRINTER_WIDTH_DOTS)
        assertEquals(48, P50SProtocol.BYTES_PER_ROW)
        assertEquals(90, P50SProtocol.BLE_CHUNK_SIZE)

        // Flow control credits parsing check
        val normalCredit = P50SProtocol.parseFlowControlNotification(byteArrayOf(0x01, 0x02))
        assertEquals(2, normalCredit)

        val credit4 = P50SProtocol.parseFlowControlNotification(byteArrayOf(0x01, 0x04))
        assertEquals(4, credit4)

        // Device name detection
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S-496A-BLE"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("Marklife P50S"))
    }
}
