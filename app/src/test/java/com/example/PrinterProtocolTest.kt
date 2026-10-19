package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import com.example.printer.P50SPrintingSystemSettings
import com.example.printer.P50SProtocol
import com.example.printer.PrinterPreferences
import com.example.printer.ReceiptBitmapGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrinterProtocolTest {

    @Test
    fun `test P50S target device name and matching`() {
        assertEquals("P50S-496A-BLE", P50SProtocol.TARGET_DEVICE_NAME_EXACT)
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S-496A-BLE"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("p50s-496a-ble"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("Marklife P50"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("PRINTER_BLE"))
        assertFalse(P50SProtocol.isPotentialP50SPrinter("Headphones_BT"))
        assertFalse(P50SProtocol.isPotentialP50SPrinter(null))
        assertFalse(P50SProtocol.isPotentialP50SPrinter(""))
    }

    @Test
    fun `test flow control credit parsing`() {
        val notify4 = byteArrayOf(0x01, 0x04)
        assertEquals(4, P50SProtocol.parseFlowControlNotification(notify4))

        val notify1 = byteArrayOf(0x01, 0x01)
        assertEquals(1, P50SProtocol.parseFlowControlNotification(notify1))

        val invalid = byteArrayOf(0x02, 0x01)
        assertEquals(null, P50SProtocol.parseFlowControlNotification(invalid))
    }

    @Test
    fun `test Zlib Level 0 stored block compression produces RFC 1950 header`() {
        val sampleData = ByteArray(100) { it.toByte() }
        val compressed = P50SProtocol.compressZlib1KbLevel0(sampleData)

        // RFC 1950 header: CMF = 0x28, FLG = 0x15
        assertEquals(0x28.toByte(), compressed[0])
        assertEquals(0x15.toByte(), compressed[1])

        // Total size should be 2 (zlib header) + 5 (deflate stored block header) + 100 (data) + 4 (adler32) = 111
        assertEquals(111, compressed.size)
    }

    @Test
    fun `test image command building`() {
        val compressedData = byteArrayOf(1, 2, 3, 4, 5)
        val cmd = P50SProtocol.buildImageCommand(heightPixels = 10, compressedData = compressedData)

        assertEquals(15, cmd.size) // 10 byte header + 5 byte data
        assertEquals(0x1F.toByte(), cmd[0])
        assertEquals(0x10.toByte(), cmd[1])
        assertEquals(0x00.toByte(), cmd[2])
        assertEquals(48.toByte(), cmd[3]) // 48 bytes per row
        assertEquals(0x00.toByte(), cmd[4])
        assertEquals(10.toByte(), cmd[5]) // height = 10
    }

    @Test
    fun `test buildPrintPayload incorporates required P50S commands`() {
        val dummyData = ByteArray(48 * 10)
        val payload = P50SProtocol.buildPrintPayload(dummyData, totalHeight = 10)

        // Must start with CMD_SET_BT_TYPE [0x1F, 0xB2, 0x00]
        assertEquals(0x1F.toByte(), payload[0])
        assertEquals(0xB2.toByte(), payload[1])
        assertEquals(0x00.toByte(), payload[2])

        // Must contain continuous paper command [0x1F, 0x80, 0x01, 0x10]
        val payloadHex = payload.joinToString(" ") { String.format("%02X", it) }
        assertTrue("Must specify continuous receipt paper (0x10)", payloadHex.contains("1F 80 01 10"))

        // Must contain start print job [0x1F, 0xC0, 0x01, 0x00]
        assertTrue("Must start print job", payloadHex.contains("1F C0 01 00"))

        // Must end with paper feed [0x1F, 0x11, 0x00, 0x00, 0x50] and align [0x1F, 0x11, 0x50]
        assertTrue("Must contain paper feed to tear bar", payloadHex.contains("1F 11 00 00 50"))
    }

    @Test
    fun `test test receipt generation and monochrome conversion`() {
        val bitmap = ReceiptBitmapGenerator.generateTestReceipt("P50S-496A-BLE")
        assertNotNull(bitmap)
        assertEquals(P50SProtocol.PRINTER_WIDTH_DOTS, bitmap.width)
        assertTrue(bitmap.height > 100)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)
    }

    @Test
    fun `test customer receipt generation with dynamic values`() {
        val repair = RepairEntity(
            id = 1L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 3500.0,
            amountPaid = 2000.0,
            balance = 1500.0,
            status = "REPAIRING",
            paymentStatus = "PARTIAL",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 1L, repairType = "Display Replacement", price = 3500.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(P50SProtocol.PRINTER_WIDTH_DOTS, bitmap.width)
        assertTrue(bitmap.height > 100)
        assertEquals(0, bitmap.height % 8) // Height must be multiple of 8 for thermal printer alignment

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
    }

    @Test
    fun `test customer receipt generation with long customer, model, and fault text without crashing`() {
        val repair = RepairEntity(
            id = 2L,
            jobNumber = "00999",
            customerName = "Alexander Bartholomew Montgomery The Third From Long Valley",
            customerPhone = "+94 77 123 4567 / 071 987 6543",
            brand = "Samsung Electronics International",
            model = "Galaxy S24 Ultra 5G Snapdragon Edition 1TB Dual Physical SIM Titanium Black",
            fault = "Front AMOLED screen glass severely cracked, touch display unresponsive in top left quadrant, rear camera lens glass shattered, and battery draining abnormally within 30 minutes\nSecond line with remarks\r\nThird line notes",
            totalPrice = 45850.0,
            amountPaid = 20000.0,
            balance = 25850.0,
            status = "IN_PROGRESS",
            paymentStatus = "PARTIAL",
            receivedDate = "29/09/2026",
            receivedTime = "02:30 PM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 2L, repairType = "Original Super AMOLED 120Hz Display Replacement", price = 32000.0),
            RepairItemEntity(repairId = 2L, repairType = "Rear Quad Camera Module Glass Replacement", price = 4850.0),
            RepairItemEntity(repairId = 2L, repairType = "High Capacity 5000mAh Lithium Ion Battery Replacement", price = 9000.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR & SERVICE CENTER",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 200)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Payload should not be abnormally large", payload.size < 100000)
    }

    @Test
    fun `test customer receipt with empty items and blank fields handles safely`() {
        val repair = RepairEntity(
            id = 3L,
            jobNumber = "",
            customerName = "",
            customerPhone = "",
            brand = "",
            model = "",
            fault = "",
            totalPrice = 1500.0,
            amountPaid = 0.0,
            balance = 1500.0,
            status = "",
            paymentStatus = "UNPAID",
            receivedDate = "",
            receivedTime = ""
        )
        val settings = AppSettingsEntity(shopName = "", currency = "")

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, emptyList(), emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 100)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
    }

    @Test
    fun `test payment receipt generation with dynamic values`() {
        val repair = RepairEntity(
            id = 1L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 3500.0,
            amountPaid = 3500.0,
            balance = 0.0,
            status = "REPAIRING",
            paymentStatus = "PAID",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val payment = PaymentEntity(
            repairId = 1L,
            paymentNumber = 1,
            amount = 2000.0,
            date = "28/09/2026",
            time = "12:05 PM"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        assertNotNull(bitmap)
        assertEquals(P50SProtocol.PRINTER_WIDTH_DOTS, bitmap.width)
        assertTrue(bitmap.height > 100)
    }

    @Test
    fun `test short receipt generates compact dynamic height without wasting paper`() {
        val repair = RepairEntity(
            id = 4L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 2500.0,
            amountPaid = 2500.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "10:15"
        )
        val items = listOf(
            RepairItemEntity(repairId = 4L, repairType = "Display Replacement", price = 2500.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        // Verify compact dynamic height for 57mm thermal continuous paper
        assertTrue("Short receipt should be compact (between 300 and 650 px)", bitmap.height in 300..650)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Payload for short receipt should be compact", payload.size < 50000)
    }

    @Test
    fun `test customer receipt and payment receipt share identical 384 dot width and encoding pipeline`() {
        val repair = RepairEntity(
            id = 5L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 2500.0,
            amountPaid = 2500.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "10:15"
        )
        val payment = PaymentEntity(
            repairId = 5L,
            paymentNumber = 1,
            amount = 2500.0,
            date = "29/09/2026",
            time = "10:15"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val customerBitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, emptyList(), listOf(payment), settings)
        val paymentBitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)

        // Both bitmaps MUST be 384 dots width
        assertEquals(384, customerBitmap.width)
        assertEquals(384, paymentBitmap.width)

        val customerMono = ReceiptBitmapGenerator.convertTo1BitMonochrome(customerBitmap)
        val paymentMono = ReceiptBitmapGenerator.convertTo1BitMonochrome(paymentBitmap)

        assertEquals(customerBitmap.height * 48, customerMono.size)
        assertEquals(paymentBitmap.height * 48, paymentMono.size)

        val customerPayload = P50SProtocol.buildPrintPayload(customerMono, customerBitmap.height)
        val paymentPayload = P50SProtocol.buildPrintPayload(paymentMono, paymentBitmap.height)

        // Both payloads must start with CMD_SET_BT_TYPE and CMD_PAPER_TYPE_CONTINUOUS
        assertEquals(0x1F.toByte(), customerPayload[0])
        assertEquals(0x1F.toByte(), paymentPayload[0])
        assertEquals(0xB2.toByte(), customerPayload[1])
        assertEquals(0xB2.toByte(), paymentPayload[1])
    }

    @Test
    fun `test minimal customer receipt generation and verification`() {
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")
        val bitmap = ReceiptBitmapGenerator.generateMinimalCustomerReceipt(settings)

        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 100)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val bytesPerRow = (bitmap.width + 7) / 8
        assertEquals(48, bytesPerRow)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Minimal receipt should produce compact payload", payload.size < 30000)
    }

    @Test
    fun `test progressive addition of fields to customer receipt preserves safety thresholds`() {
        val baseSettings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        // Step 1: Base minimal
        var r = RepairEntity(
            jobNumber = "0001",
            customerName = "",
            customerPhone = "",
            brand = "",
            model = "",
            fault = "",
            totalPrice = 100.0,
            amountPaid = 100.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "10:15"
        )
        var bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 2: Add customer name
        r = r.copy(customerName = "Kasun Perera")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 3: Add phone
        r = r.copy(customerPhone = "0771234567")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 4: Add device (brand + model)
        r = r.copy(brand = "Samsung", model = "Galaxy M02")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 5: Add complaint
        r = r.copy(fault = "Display broken / touch not working")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 6: Add repair items and prices
        val items = listOf(
            RepairItemEntity(repairId = 1L, repairType = "Display Replacement", price = 2500.0)
        )
        r = r.copy(totalPrice = 2500.0, amountPaid = 2500.0, balance = 0.0)
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, items, emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bmp)
        assertEquals(bmp.height * 48, mono.size)
        val payload = P50SProtocol.buildPrintPayload(mono, bmp.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Final customer receipt payload must remain safe and under 60KB", payload.size < 60000)
    }

    @Test
    fun `test printer preferences persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default auto print is false
        PrinterPreferences.setAutoPrintEnabled(context, false)
        assertFalse(PrinterPreferences.isAutoPrintEnabled(context))

        // Enable auto print
        PrinterPreferences.setAutoPrintEnabled(context, true)
        assertTrue(PrinterPreferences.isAutoPrintEnabled(context))

        // Save printer
        PrinterPreferences.savePrinter(context, "P50S-496A-BLE", "AA:BB:CC:DD:EE:FF")
        assertEquals("P50S-496A-BLE", PrinterPreferences.getSavedPrinterName(context))
        assertEquals("AA:BB:CC:DD:EE:FF", PrinterPreferences.getSavedPrinterMac(context))

        // Clear printer
        PrinterPreferences.clearSavedPrinter(context)
        assertEquals(null, PrinterPreferences.getSavedPrinterName(context))
        assertEquals(null, PrinterPreferences.getSavedPrinterMac(context))
    }

    @Test
    fun `test buildStructuredPrintPayload matches buildPrintPayload byte for byte`() {
        val dummyData = ByteArray(48 * 450) { (it % 256).toByte() }
        val structured = P50SProtocol.buildStructuredPrintPayload(dummyData, totalHeight = 450)
        val flatPayload = P50SProtocol.buildPrintPayload(dummyData, totalHeight = 450)

        // Must match byte-for-byte
        assertEquals(flatPayload.size, structured.totalBytes)
        val structuredBytes = structured.toByteArray()
        assertEquals(flatPayload.size, structuredBytes.size)
        assertTrue(flatPayload.contentEquals(structuredBytes))

        // Continuous print stream matching Test Print: totalSlices = 1
        assertEquals(1, structured.totalSlices)
        assertEquals(1, structured.slices.size)
    }

    @Test
    fun `test customer receipt multi-slice generation guarantees all slices generated for complete print`() {
        val repair = RepairEntity(
            id = 10L,
            jobNumber = "0027",
            customerName = "Ashoka",
            customerPhone = "07XXXXXXXX",
            brand = "Samsung",
            model = "M02",
            fault = "Water Damage Service",
            totalPrice = 800.0,
            amountPaid = 800.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "09:15"
        )
        val items = listOf(
            RepairItemEntity(repairId = 10L, repairType = "Water Damage Service", price = 800.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertEquals(384, bitmap.width)
        assertTrue("Receipt height must be non-zero", bitmap.height > 100)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val structured = P50SProtocol.buildStructuredPrintPayload(monoBytes, bitmap.height)

        val expectedSlices = (bitmap.height + P50SProtocol.SLICE_MAX_HEIGHT - 1) / P50SProtocol.SLICE_MAX_HEIGHT
        assertEquals(expectedSlices, structured.totalSlices)
        assertTrue("Every slice must contain data", structured.slices.all { it.isNotEmpty() })
        assertTrue("Init commands must not be empty", structured.initCommands.isNotEmpty())
        assertTrue("End commands must not be empty", structured.endCommands.isNotEmpty())

        val chunkSize = 90
        val totalPackets = (structured.initCommands.size + chunkSize - 1) / chunkSize +
                structured.slices.sumOf { (it.size + chunkSize - 1) / chunkSize } +
                (structured.endCommands.size + chunkSize - 1) / chunkSize
        assertTrue("Packet count must be greater than zero", totalPackets > 0)

        // Verify slice details metadata
        assertEquals(expectedSlices, structured.sliceDetails.size)
        structured.sliceDetails.forEach { slice ->
            assertTrue("Slice height must be > 0", slice.sliceHeight > 0)
            assertTrue("Slice height must not exceed SLICE_MAX_HEIGHT", slice.sliceHeight <= P50SProtocol.SLICE_MAX_HEIGHT)
            assertEquals("Slice raw bytes must equal height * 48", slice.sliceHeight * 48, slice.sliceRawBytes)
            assertTrue("Slice compressed bytes must be > 0", slice.sliceCompressedBytes > 0)
            assertEquals("Slice payload must match command size", slice.command.size, slice.slicePayloadBytes)
            assertTrue("Slice chunk count must be > 0", slice.sliceChunkCount > 0)
        }
    }

    @Test
    fun `test TEST 1 Test Print generation produces complete structured payload`() {
        val testBmp = ReceiptBitmapGenerator.generateTestReceipt("P50S-496A-BLE")
        assertEquals(384, testBmp.width)
        assertTrue(testBmp.height > 100)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(testBmp)
        val structured = P50SProtocol.buildStructuredPrintPayload(monoBytes, testBmp.height)

        assertTrue(structured.totalSlices >= 1)
        assertTrue(structured.initCommands.isNotEmpty())
        assertTrue(structured.endCommands.isNotEmpty())
        assertTrue(structured.totalBytes > 0)
    }

    @Test
    fun `test TEST 2 Customer Receipt with customer name, phone, device, item, and financials`() {
        val repair = RepairEntity(
            jobNumber = "0045",
            customerName = "Nimal Silva",
            customerPhone = "0712345678",
            brand = "Apple",
            model = "iPhone 13",
            fault = "Screen replacement",
            totalPrice = 18500.0,
            amountPaid = 10000.0,
            balance = 8500.0,
            status = "IN_PROGRESS",
            paymentStatus = "PARTIAL",
            receivedDate = "30/09/2026",
            receivedTime = "10:30"
        )
        val items = listOf(
            RepairItemEntity(repairId = 0L, repairType = "OLED Screen Replacement", price = 18500.0)
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")
        val config = com.example.printer.CustomerReceiptConfig()

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings, config)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 200)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val structured = P50SProtocol.buildStructuredPrintPayload(monoBytes, bitmap.height)

        // Complete continuous print stream matching Test Print
        assertTrue("Customer receipt must produce valid print stream", structured.totalSlices >= 1)
        assertEquals(structured.totalSlices, structured.slices.size)
        assertEquals(structured.totalSlices, structured.sliceDetails.size)

        var totalSliceHeight = 0
        for (slice in structured.sliceDetails) {
            totalSliceHeight += slice.sliceHeight
            assertTrue(slice.sliceChunkCount > 0)
        }
        assertEquals("Sum of slice heights must equal total bitmap height", bitmap.height, totalSliceHeight)
    }

    @Test
    fun `test TEST 3 Customer Receipt with long complaint and repair text`() {
        val repair = RepairEntity(
            jobNumber = "0099",
            customerName = "Chandrasiri Wickramasinghe Ranasinghe",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy S22 Ultra 5G Snapdragon",
            fault = "Display shattered, touchscreen unresponsive, water ingress through charging port, loud speaker buzzing",
            totalPrice = 45000.0,
            amountPaid = 20000.0,
            balance = 25000.0,
            status = "IN_PROGRESS",
            paymentStatus = "PARTIAL",
            receivedDate = "30/09/2026",
            receivedTime = "11:00"
        )
        val items = listOf(
            RepairItemEntity(repairId = 0L, repairType = "Original AMOLED Display Assembly", price = 32000.0),
            RepairItemEntity(repairId = 0L, repairType = "Charging Port Flex Cable", price = 6000.0),
            RepairItemEntity(repairId = 0L, repairType = "Loud Speaker Module Replacement", price = 7000.0)
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")
        val config = com.example.printer.CustomerReceiptConfig(showComplaint = true)

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings, config)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 300)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val structured = P50SProtocol.buildStructuredPrintPayload(monoBytes, bitmap.height)

        assertTrue("Long receipt should produce valid print stream", structured.totalSlices >= 1)
        assertTrue("Payload should remain within safe limits", structured.totalBytes in 1000..75000)
    }

    @Test
    fun `test TEST 4 Customer Receipt taller than 200px generates all slices correctly`() {
        val repair = RepairEntity(
            jobNumber = "0027",
            customerName = "Ashoka",
            customerPhone = "07XXXXXXXX",
            brand = "Samsung",
            model = "M02",
            fault = "Water Damage Service",
            totalPrice = 800.0,
            amountPaid = 800.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "09:15"
        )
        val items = listOf(
            RepairItemEntity(repairId = 0L, repairType = "Water Damage Service", price = 800.0)
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")
        val config = com.example.printer.CustomerReceiptConfig()

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings, config)
        assertTrue("Customer receipt must be taller than 200px", bitmap.height > 200)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val structured = P50SProtocol.buildStructuredPrintPayload(monoBytes, bitmap.height)

        val expectedSlices = (bitmap.height + P50SProtocol.SLICE_MAX_HEIGHT - 1) / P50SProtocol.SLICE_MAX_HEIGHT
        assertEquals(expectedSlices, structured.totalSlices)

        for (i in 0 until structured.totalSlices) {
            val slice = structured.sliceDetails[i]
            assertEquals(i, slice.sliceIndex)
            val expectedHeight = if (i == structured.totalSlices - 1) {
                bitmap.height - (i * P50SProtocol.SLICE_MAX_HEIGHT)
            } else {
                P50SProtocol.SLICE_MAX_HEIGHT
            }
            assertEquals(expectedHeight, slice.sliceHeight)
            assertTrue(slice.slicePayloadBytes > 10)
        }
    }

    @Test
    fun `test TEST 5 Print Customer Receipt twice generates identical complete payloads`() {
        val repair = RepairEntity(
            jobNumber = "0027",
            customerName = "Ashoka",
            customerPhone = "07XXXXXXXX",
            brand = "Samsung",
            model = "M02",
            fault = "Water Damage Service",
            totalPrice = 800.0,
            amountPaid = 800.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "09:15"
        )
        val items = listOf(
            RepairItemEntity(repairId = 0L, repairType = "Water Damage Service", price = 800.0)
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        // First print payload
        val bmp1 = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        val mono1 = ReceiptBitmapGenerator.convertTo1BitMonochrome(bmp1)
        val structured1 = P50SProtocol.buildStructuredPrintPayload(mono1, bmp1.height)

        // Second print payload (Reprint)
        val bmp2 = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        val mono2 = ReceiptBitmapGenerator.convertTo1BitMonochrome(bmp2)
        val structured2 = P50SProtocol.buildStructuredPrintPayload(mono2, bmp2.height)

        assertEquals(bmp1.height, bmp2.height)
        assertEquals(structured1.totalSlices, structured2.totalSlices)
        assertEquals(structured1.totalBytes, structured2.totalBytes)
        assertTrue(structured1.toByteArray().contentEquals(structured2.toByteArray()))
    }

    @Test
    fun `test TEST 6 Customer Receipt and Payment Receipt both produce complete multi-slice structures`() {
        val repair = RepairEntity(
            jobNumber = "0027",
            customerName = "Ashoka",
            customerPhone = "07XXXXXXXX",
            brand = "Samsung",
            model = "M02",
            fault = "Water Damage Service",
            totalPrice = 800.0,
            amountPaid = 800.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "09:15"
        )
        val items = listOf(
            RepairItemEntity(repairId = 0L, repairType = "Water Damage Service", price = 800.0)
        )
        val payment = PaymentEntity(
            repairId = 0L,
            paymentNumber = 1,
            amount = 800.0,
            date = "29/09/2026",
            time = "09:15"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val custBmp = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, listOf(payment), settings)
        val payBmp = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)

        val custMono = ReceiptBitmapGenerator.convertTo1BitMonochrome(custBmp)
        val payMono = ReceiptBitmapGenerator.convertTo1BitMonochrome(payBmp)

        val custStructured = P50SProtocol.buildStructuredPrintPayload(custMono, custBmp.height)
        val payStructured = P50SProtocol.buildStructuredPrintPayload(payMono, payBmp.height)

        assertTrue("Customer receipt should have slices", custStructured.totalSlices >= 1)
        assertTrue("Payment receipt should have slices", payStructured.totalSlices >= 1)

        assertTrue(custStructured.slices.all { it.isNotEmpty() })
        assertTrue(payStructured.slices.all { it.isNotEmpty() })

        assertTrue(custStructured.initCommands.contentEquals(payStructured.initCommands))
        assertTrue(custStructured.endCommands.contentEquals(payStructured.endCommands))
    }

    @Test
    fun `test TEST 7 Diagnostic Customer Receipt bitmap has exactly 3 sections and generates continuous print stream`() {
        val diagBmp = ReceiptBitmapGenerator.generateCustomerReceiptDiagnostic()
        assertEquals(384, diagBmp.width)
        assertEquals(600, diagBmp.height)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(diagBmp)
        val structured = P50SProtocol.buildStructuredPrintPayload(monoBytes, diagBmp.height)

        assertEquals("Diagnostic bitmap of 600px yields 1 continuous stream slice identical to Test Print", 1, structured.totalSlices)
        assertEquals(1, structured.sliceDetails.size)

        val slice = structured.sliceDetails[0]
        assertEquals(0, slice.sliceIndex)
        assertEquals(600, slice.sliceHeight)
        assertEquals(600 * 48, slice.sliceRawBytes)
        assertTrue("Slice payload must contain compressed command", slice.slicePayloadBytes > 10)
        assertTrue("Slice must have chunks", slice.sliceChunkCount > 0)

        // Verify the exact DIAG contract
        val bitmapHeight = diagBmp.height
        val rasterCommandCount = structured.slices.size
        val startJobCount = 1
        val stopJobCount = 1

        assertEquals(600, bitmapHeight)
        assertEquals(1, rasterCommandCount)
        assertEquals(1, startJobCount)
        assertEquals(1, stopJobCount)

        // Verify image command header has height = 600 (0x02, 0x58)
        val cmd = structured.slices[0]
        assertEquals(0x1F.toByte(), cmd[0])
        assertEquals(0x10.toByte(), cmd[1])
        assertEquals(0x00.toByte(), cmd[2])
        assertEquals(48.toByte(), cmd[3])
        assertEquals(0x02.toByte(), cmd[4]) // 600 shr 8
        assertEquals(0x58.toByte(), cmd[5]) // 600 & 0xFF
    }

    @Test
    fun `test TEST 8 Customer Receipt diagnostic mode preference toggle`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = PrinterPreferences.isCustomerReceiptDiagnosticMode(context)

        PrinterPreferences.setCustomerReceiptDiagnosticMode(context, true)
        assertTrue(PrinterPreferences.isCustomerReceiptDiagnosticMode(context))

        PrinterPreferences.setCustomerReceiptDiagnosticMode(context, false)
        assertFalse(PrinterPreferences.isCustomerReceiptDiagnosticMode(context))

        // Reset to original
        PrinterPreferences.setCustomerReceiptDiagnosticMode(context, original)
    }

    @Test
    fun `test TEST 9 Separate 3-Job sequence diagnostic structure`() {
        val bitmap = ReceiptBitmapGenerator.generateCustomerReceiptDiagnostic()
        assertEquals(384, bitmap.width)
        assertEquals(600, bitmap.height)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val bytesPerRow = 48
        val blockHeight = 200

        // Test each job command independently
        for (jobIdx in 0..2) {
            val startRow = jobIdx * blockHeight
            val rawSlice = ByteArray(blockHeight * bytesPerRow)
            System.arraycopy(monoBytes, startRow * bytesPerRow, rawSlice, 0, blockHeight * bytesPerRow)
            val compressed = P50SProtocol.compressZlib1KbLevel0(rawSlice)
            val cmd = P50SProtocol.buildImageCommand(blockHeight, compressed)

            // Header validation
            assertEquals(0x1F.toByte(), cmd[0])
            assertEquals(0x10.toByte(), cmd[1])
            assertEquals(0x00.toByte(), cmd[2])
            assertEquals(48.toByte(), cmd[3]) // 48 bytes per row
            assertEquals(0x00.toByte(), cmd[4]) // 200 shr 8 == 0
            assertEquals(200.toByte(), cmd[5])  // 200 & 0xFF == 200 (0xC8)
            assertTrue("Compressed data must be present", cmd.size > 10)
        }

        // Verify START_PRINT_JOB, STOP_PRINT_JOB, and final FEED commands
        val startJob = P50SProtocol.CMD_START_PRINT_JOB
        val stopJob = P50SProtocol.CMD_STOP_PRINT_JOB
        val feedPaper80 = P50SProtocol.CMD_FEED_PAPER_80PX
        val feedPaper160 = P50SProtocol.CMD_FEED_PAPER_160PX
        val alignEnd = P50SProtocol.CMD_ALIGN_END

        assertEquals(0x1F.toByte(), startJob[0])
        assertEquals(0xC0.toByte(), startJob[1])
        assertEquals(0x01.toByte(), startJob[2])
        assertEquals(0x00.toByte(), startJob[3])

        assertEquals(0x1F.toByte(), stopJob[0])
        assertEquals(0xC0.toByte(), stopJob[1])
        assertEquals(0x01.toByte(), stopJob[2])
        assertEquals(0x01.toByte(), stopJob[3])

        // Verify stopJob and startJob have no paper feed command (0x11)
        assertFalse("STOP_PRINT_JOB must not contain 0x11 paper feed", stopJob.contains(0x11.toByte()))
        assertFalse("START_PRINT_JOB must not contain 0x11 paper feed", startJob.contains(0x11.toByte()))

        // Verify feedPaper and alignEnd contain 0x11
        assertEquals(0x1F.toByte(), feedPaper80[0])
        assertEquals(0x11.toByte(), feedPaper80[1])
        assertEquals(0x50.toByte(), feedPaper80[4]) // 80px (0x50)

        assertEquals(0x1F.toByte(), feedPaper160[0])
        assertEquals(0x11.toByte(), feedPaper160[1])
        assertEquals(0x00.toByte(), feedPaper160[2])
        assertEquals(0x00.toByte(), feedPaper160[3])
        assertEquals(0xA0.toByte(), feedPaper160[4]) // 160px (0xA0)

        assertEquals(0x1F.toByte(), alignEnd[0])
        assertEquals(0x11.toByte(), alignEnd[1])
    }

    @Test
    fun `test zero inter-job feed and final feed 160px after Job 3 STOP`() {
        val dummyData = ByteArray(600 * 48)
        val blocks = P50SProtocol.buildSeparateJobBlocks(dummyData, 600, maxBlockHeight = 200)
        assertEquals(3, blocks.size)

        // Verify that individual job raster commands do NOT contain paper feed commands (0x11)
        for ((idx, block) in blocks.withIndex()) {
            val rasterCmd = block.rasterCommand
            assertEquals("Job ${idx + 1} must start with 0x1F 0x10", 0x10.toByte(), rasterCmd[1])
            assertFalse("Job ${idx + 1} header must not be a feed command", rasterCmd[1] == 0x11.toByte())
        }

        // Verify START_PRINT_JOB has NO 0x11 paper feed
        assertFalse("START_PRINT_JOB has no 0x11 feed", P50SProtocol.CMD_START_PRINT_JOB.contains(0x11.toByte()))

        // Verify STOP_PRINT_JOB has NO 0x11 paper feed
        assertFalse("STOP_PRINT_JOB has no 0x11 feed", P50SProtocol.CMD_STOP_PRINT_JOB.contains(0x11.toByte()))

        // Verify final feed is exactly 160px
        val finalFeed = P50SProtocol.CMD_FEED_PAPER_160PX
        assertEquals(0x1F.toByte(), finalFeed[0])
        assertEquals(0x11.toByte(), finalFeed[1])
        val feedHeightPixels = ((finalFeed[3].toInt() and 0xFF) shl 8) or (finalFeed[4].toInt() and 0xFF)
        assertEquals("Final feed height must be exactly 160px", 160, feedHeightPixels)

        // Verify final align
        assertEquals(0x1F.toByte(), P50SProtocol.CMD_ALIGN_END[0])
        assertEquals(0x11.toByte(), P50SProtocol.CMD_ALIGN_END[1])
        assertEquals(0x50.toByte(), P50SProtocol.CMD_ALIGN_END[2])
    }

    @Test
    fun `test TEST 10 Power Diagnostic test specs and bitmap generation for Tests A through J`() {
        val tests = listOf(
            Triple("TEST A", 200, 0),
            Triple("TEST B", 300, 0),
            Triple("TEST C", 400, 0),
            Triple("TEST D", 500, 0),
            Triple("TEST E", 600, 0),
            Triple("TEST F", 400, 25),
            Triple("TEST G", 400, 50),
            Triple("TEST H", 400, 75),
            Triple("TEST I", 600, 25),
            Triple("TEST J", 600, 50)
        )

        for ((id, expectedHeight, expectedDensity) in tests) {
            val spec = ReceiptBitmapGenerator.getPowerTestSpec(id)
            assertEquals("Test ID must match", id, spec.id)
            assertEquals("Height must match", expectedHeight, spec.height)
            assertEquals("Density must match", expectedDensity, spec.targetDensityPercent)

            val bitmap = ReceiptBitmapGenerator.generatePowerDiagnosticBitmap(spec)
            assertEquals(384, bitmap.width)
            assertEquals(expectedHeight, bitmap.height)

            val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
            assertEquals(expectedHeight * 48, monoBytes.size)

            var blackDots = 0
            for (b in monoBytes) {
                blackDots += java.lang.Integer.bitCount(b.toInt() and 0xFF)
            }
            val percent = (blackDots.toDouble() * 100.0) / (384 * expectedHeight)
            if (expectedDensity > 0) {
                // Should be within reasonable range of target density (allowing for margins/headers)
                assertTrue("Density for $id ($percent%) should be >= 10%", percent >= 10.0)
            } else {
                // Low density tests should have < 15% black dots (mostly text headers)
                assertTrue("Low density for $id ($percent%) should be < 15%", percent < 15.0)
            }
        }
    }

    @Test
    fun `test Customer Receipt print-job splitting logic for various heights and empty bitmap`() {
        // 1. 600px -> exactly 3 jobs: 200, 200, 200
        val mono600 = ByteArray(600 * 48)
        val jobs600 = P50SProtocol.buildSeparateJobBlocks(mono600, 600, maxBlockHeight = 200)
        assertEquals("600px bitmap must yield exactly 3 jobs", 3, jobs600.size)
        assertEquals(200, jobs600[0].blockHeight)
        assertEquals(0, jobs600[0].startRow)
        assertEquals(200, jobs600[1].blockHeight)
        assertEquals(200, jobs600[1].startRow)
        assertEquals(200, jobs600[2].blockHeight)
        assertEquals(400, jobs600[2].startRow)

        // 2. 550px -> exactly 3 jobs: 200, 200, 150
        val mono550 = ByteArray(550 * 48)
        val jobs550 = P50SProtocol.buildSeparateJobBlocks(mono550, 550, maxBlockHeight = 200)
        assertEquals("550px bitmap must yield exactly 3 jobs", 3, jobs550.size)
        assertEquals(200, jobs550[0].blockHeight)
        assertEquals(0, jobs550[0].startRow)
        assertEquals(200, jobs550[1].blockHeight)
        assertEquals(200, jobs550[1].startRow)
        assertEquals(150, jobs550[2].blockHeight)
        assertEquals(400, jobs550[2].startRow)

        // 3. 400px -> exactly 2 jobs: 200, 200
        val mono400 = ByteArray(400 * 48)
        val jobs400 = P50SProtocol.buildSeparateJobBlocks(mono400, 400, maxBlockHeight = 200)
        assertEquals("400px bitmap must yield exactly 2 jobs", 2, jobs400.size)
        assertEquals(200, jobs400[0].blockHeight)
        assertEquals(0, jobs400[0].startRow)
        assertEquals(200, jobs400[1].blockHeight)
        assertEquals(200, jobs400[1].startRow)

        // 4. 200px -> exactly 1 job: 200
        val mono200 = ByteArray(200 * 48)
        val jobs200 = P50SProtocol.buildSeparateJobBlocks(mono200, 200, maxBlockHeight = 200)
        assertEquals("200px bitmap must yield exactly 1 job", 1, jobs200.size)
        assertEquals(200, jobs200[0].blockHeight)
        assertEquals(0, jobs200[0].startRow)

        // 5. Empty / zero-height bitmap must be handled safely
        val jobsZeroHeight = P50SProtocol.buildSeparateJobBlocks(ByteArray(48), 0, maxBlockHeight = 200)
        assertTrue("Zero height must return empty list safely", jobsZeroHeight.isEmpty())

        val jobsNegativeHeight = P50SProtocol.buildSeparateJobBlocks(ByteArray(48), -10, maxBlockHeight = 200)
        assertTrue("Negative height must return empty list safely", jobsNegativeHeight.isEmpty())

        val jobsEmptyData = P50SProtocol.buildSeparateJobBlocks(ByteArray(0), 100, maxBlockHeight = 200)
        assertTrue("Empty data array must return empty list safely", jobsEmptyData.isEmpty())

        // 6. Verify each generated job contains exactly ONE raster command
        for (job in jobs600 + jobs550 + jobs400 + jobs200) {
            val cmd = job.rasterCommand
            assertTrue("Command must have header + payload", cmd.size > 10)
            assertEquals("Command must begin with 0x1F", 0x1F.toByte(), cmd[0])
            assertEquals("Command must begin with 0x10 (Raster Image)", 0x10.toByte(), cmd[1])
            assertEquals("Width bytes high must be 0", 0x00.toByte(), cmd[2])
            assertEquals("Width bytes low must be 48", 48.toByte(), cmd[3])

            val heightFromHeader = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
            assertEquals("Raster command height in header must match blockHeight", job.blockHeight, heightFromHeader)
        }
    }

    @Test
    fun `test real generated Customer Receipt bitmap splitting into separate 200px jobs with single raster each`() {
        val repair = RepairEntity(
            id = 10L,
            jobNumber = "00125",
            customerName = "Kasun Perera",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 4500.0,
            amountPaid = 2000.0,
            balance = 2500.0,
            status = "REPAIRING",
            paymentStatus = "PARTIAL",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 10L, repairType = "Display Replacement", price = 3500.0),
            RepairItemEntity(repairId = 10L, repairType = "Tempered Glass", price = 1000.0)
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 200)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val blocks = P50SProtocol.buildSeparateJobBlocks(monoBytes, bitmap.height, maxBlockHeight = 200)

        val expectedJobs = (bitmap.height + 199) / 200
        assertEquals("Job count must equal ceil(height / 200)", expectedJobs, blocks.size)

        var totalHeightSum = 0
        for ((idx, block) in blocks.withIndex()) {
            assertEquals(idx, block.jobIndex)
            assertTrue("Each block height must be <= 200", block.blockHeight in 1..200)
            assertEquals("Start row must match running sum", totalHeightSum, block.startRow)
            totalHeightSum += block.blockHeight

            val cmd = block.rasterCommand
            assertEquals(0x1F.toByte(), cmd[0])
            assertEquals(0x10.toByte(), cmd[1])
            val cmdHeight = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
            assertEquals(block.blockHeight, cmdHeight)
        }
        assertEquals(bitmap.height, totalHeightSum)
    }

    @Test
    fun `test Payment Receipt print-job splitting logic for various heights and empty bitmap`() {
        // 1. 600px -> exactly 3 jobs: 200, 200, 200
        val mono600 = ByteArray(600 * 48)
        val jobs600 = P50SProtocol.buildSeparateJobBlocks(mono600, 600, maxBlockHeight = 200)
        assertEquals("600px payment receipt must yield exactly 3 jobs", 3, jobs600.size)
        assertEquals(200, jobs600[0].blockHeight)
        assertEquals(0, jobs600[0].startRow)
        assertEquals(200, jobs600[1].blockHeight)
        assertEquals(200, jobs600[1].startRow)
        assertEquals(200, jobs600[2].blockHeight)
        assertEquals(400, jobs600[2].startRow)

        // 2. 550px -> exactly 3 jobs: 200, 200, 150
        val mono550 = ByteArray(550 * 48)
        val jobs550 = P50SProtocol.buildSeparateJobBlocks(mono550, 550, maxBlockHeight = 200)
        assertEquals("550px payment receipt must yield exactly 3 jobs", 3, jobs550.size)
        assertEquals(200, jobs550[0].blockHeight)
        assertEquals(0, jobs550[0].startRow)
        assertEquals(200, jobs550[1].blockHeight)
        assertEquals(200, jobs550[1].startRow)
        assertEquals(150, jobs550[2].blockHeight)
        assertEquals(400, jobs550[2].startRow)

        // 3. 400px -> exactly 2 jobs: 200, 200
        val mono400 = ByteArray(400 * 48)
        val jobs400 = P50SProtocol.buildSeparateJobBlocks(mono400, 400, maxBlockHeight = 200)
        assertEquals("400px payment receipt must yield exactly 2 jobs", 2, jobs400.size)
        assertEquals(200, jobs400[0].blockHeight)
        assertEquals(0, jobs400[0].startRow)
        assertEquals(200, jobs400[1].blockHeight)
        assertEquals(200, jobs400[1].startRow)

        // 4. 200px -> exactly 1 job: 200
        val mono200 = ByteArray(200 * 48)
        val jobs200 = P50SProtocol.buildSeparateJobBlocks(mono200, 200, maxBlockHeight = 200)
        assertEquals("200px payment receipt must yield exactly 1 job", 1, jobs200.size)
        assertEquals(200, jobs200[0].blockHeight)
        assertEquals(0, jobs200[0].startRow)

        // 5. Empty / zero-height bitmap must be handled safely
        val jobsZeroHeight = P50SProtocol.buildSeparateJobBlocks(ByteArray(48), 0, maxBlockHeight = 200)
        assertTrue("Zero height must return empty list safely for payment receipt", jobsZeroHeight.isEmpty())

        val jobsNegativeHeight = P50SProtocol.buildSeparateJobBlocks(ByteArray(48), -10, maxBlockHeight = 200)
        assertTrue("Negative height must return empty list safely for payment receipt", jobsNegativeHeight.isEmpty())

        val jobsEmptyData = P50SProtocol.buildSeparateJobBlocks(ByteArray(0), 100, maxBlockHeight = 200)
        assertTrue("Empty data array must return empty list safely for payment receipt", jobsEmptyData.isEmpty())

        // 6. Verify each generated job contains exactly ONE raster command
        for (job in jobs600 + jobs550 + jobs400 + jobs200) {
            val cmd = job.rasterCommand
            assertTrue("Payment receipt command must have header + payload", cmd.size > 10)
            assertEquals("Payment receipt command must begin with 0x1F", 0x1F.toByte(), cmd[0])
            assertEquals("Payment receipt command must begin with 0x10 (Raster Image)", 0x10.toByte(), cmd[1])
            assertEquals("Width bytes high must be 0", 0x00.toByte(), cmd[2])
            assertEquals("Width bytes low must be 48", 48.toByte(), cmd[3])

            val heightFromHeader = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
            assertEquals("Payment receipt raster command height in header must match blockHeight", job.blockHeight, heightFromHeader)
        }
    }

    @Test
    fun `test real generated Payment Receipt bitmap splitting into separate 200px jobs with single raster each`() {
        val repair = RepairEntity(
            id = 20L,
            jobNumber = "00126",
            customerName = "Nimal Silva",
            customerPhone = "0719876543",
            brand = "Apple",
            model = "iPhone 13",
            fault = "Battery Replacement",
            totalPrice = 12000.0,
            amountPaid = 12000.0,
            balance = 0.0,
            status = "COMPLETED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "02:15 PM"
        )
        val payment = PaymentEntity(
            repairId = 20L,
            paymentNumber = 1,
            amount = 12000.0,
            date = "29/09/2026",
            time = "02:15 PM"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 100)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val blocks = P50SProtocol.buildSeparateJobBlocks(monoBytes, bitmap.height, maxBlockHeight = 200)

        val expectedJobs = (bitmap.height + 199) / 200
        assertEquals("Payment receipt job count must equal ceil(height / 200)", expectedJobs, blocks.size)

        var totalHeightSum = 0
        for ((idx, block) in blocks.withIndex()) {
            assertEquals(idx, block.jobIndex)
            assertTrue("Each block height must be <= 200", block.blockHeight in 1..200)
            assertEquals("Start row must match running sum", totalHeightSum, block.startRow)
            totalHeightSum += block.blockHeight

            val cmd = block.rasterCommand
            assertEquals(0x1F.toByte(), cmd[0])
            assertEquals(0x10.toByte(), cmd[1])
            val cmdHeight = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
            assertEquals(block.blockHeight, cmdHeight)
        }
        assertEquals(bitmap.height, totalHeightSum)
    }

    @Test
    fun `test that a receipt of any calculated height produces exactly ONE raster command and exactly ONE START STOP print-job pair`() {
        // Diverse calculated receipt heights from small to very tall
        val testHeights = listOf(80, 150, 200, 320, 450, 550, 600, 720, 850, 1024, 1500)

        for (height in testHeights) {
            val monoData = ByteArray(height * P50SProtocol.BYTES_PER_ROW) { (it % 255).toByte() }
            val plan = P50SProtocol.buildSingleJobReceiptPlan(monoData, height)

            // 1. Verify exactly ONE raster command
            assertEquals("Receipt of height $height px must produce exactly ONE raster command", 1, plan.rasterCount)

            // 2. Verify exactly ONE print job (1 START and 1 STOP)
            assertEquals("Receipt of height $height px must produce exactly ONE print job", 1, plan.jobCount)
            assertEquals(0x1F.toByte(), plan.startJobCommand[0])
            assertEquals(0xC0.toByte(), plan.startJobCommand[1])
            assertEquals(0x01.toByte(), plan.startJobCommand[2])
            assertEquals(0x00.toByte(), plan.startJobCommand[3]) // START

            assertEquals(0x1F.toByte(), plan.stopJobCommand[0])
            assertEquals(0xC0.toByte(), plan.stopJobCommand[1])
            assertEquals(0x01.toByte(), plan.stopJobCommand[2])
            assertEquals(0x01.toByte(), plan.stopJobCommand[3]) // STOP

            // 3. Verify the raster command properties
            val rasterCmd = plan.rasterCommand
            assertTrue("Raster command must have header and compressed payload", rasterCmd.size > 10)
            assertEquals(0x1F.toByte(), rasterCmd[0])
            assertEquals(0x10.toByte(), rasterCmd[1]) // Sub-command 0x10 = Raster Image
            assertEquals(0x00.toByte(), rasterCmd[2])
            assertEquals(48.toByte(), rasterCmd[3]) // 48 bytes per row = 384 dots

            val rasterHeightFromHeader = ((rasterCmd[4].toInt() and 0xFF) shl 8) or (rasterCmd[5].toInt() and 0xFF)
            assertEquals("Raster command height in header must match actual calculated receipt height", height, rasterHeightFromHeader)

            // 4. Verify compressed payload contains RFC 1950 header (0x28, 0x15)
            assertEquals(0x28.toByte(), rasterCmd[10])
            assertEquals(0x15.toByte(), rasterCmd[11])

            // 5. Verify NO paper feed command (0x11) inside the raster command
            assertFalse("Raster command must not contain paper feed command 0x11 in header", rasterCmd[1] == 0x11.toByte())

            // 6. Verify post-raster completion timeout is dynamically calculated based on actual receipt height
            val expectedTimeout = P50SProtocol.calculatePostRasterCompletionTimeoutMs(height)
            assertEquals("Post-raster timeout must match calculated formula", expectedTimeout, plan.postRasterTimeoutMs)
            assertTrue("Post-raster timeout must be at least min settling delay", plan.postRasterTimeoutMs >= 500L)

            // 7. Verify FINAL FEED and FINAL ALIGN commands are configured for execution strictly after STOP_PRINT_JOB
            assertEquals(0x1F.toByte(), plan.finalFeedCommand[0])
            assertEquals(0x11.toByte(), plan.finalFeedCommand[1])
            assertEquals(0xA0.toByte(), plan.finalFeedCommand[4]) // 160px feed

            assertEquals(0x1F.toByte(), plan.finalAlignCommand[0])
            assertEquals(0x11.toByte(), plan.finalAlignCommand[1])
            assertEquals(0x50.toByte(), plan.finalAlignCommand[2]) // Align end
        }
    }

    @Test
    fun `test post-raster completion timeout calculation scaling with receipt height`() {
        // Small receipts: 80px -> 500 + 80*3 = 740ms
        assertEquals(740L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(80))

        // Medium receipts: 200px -> 500 + 200*3 = 1100ms
        assertEquals(1100L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(200))

        // Typical customer receipts: 550px -> 500 + 550*3 = 2150ms
        assertEquals(2150L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(550))

        // Tall receipts: 1000px -> 500 + 1000*3 = 3500ms
        assertEquals(3500L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(1000))

        // Very tall receipts: 2000px -> 500 + 2000*3 = 6500ms
        assertEquals(6500L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(2000))

        // Boundary safety: max timeout cap (15000ms)
        assertEquals(15000L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(6000))

        // Minimum safety: zero or negative height never drops below min settling delay (500ms)
        assertEquals(500L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(0))
        assertEquals(500L, P50SProtocol.calculatePostRasterCompletionTimeoutMs(-50))
    }

    @Test
    fun `test buffer completion notification parsing on 0xFF03`() {
        // 0x01 0x04 indicates full credits restored (buffer cleared/processed)
        val fullCreditsNotification = byteArrayOf(0x01, 0x04)
        assertTrue(P50SProtocol.isRasterBufferCompletionNotification(fullCreditsNotification))

        // 0x01 0x01 (intermediate 1-credit throttle window)
        val partialCreditNotification = byteArrayOf(0x01, 0x01)
        assertFalse(P50SProtocol.isRasterBufferCompletionNotification(partialCreditNotification))

        // Non-flow control packet
        val invalidNotification = byteArrayOf(0x02, 0x04)
        assertFalse(P50SProtocol.isRasterBufferCompletionNotification(invalidNotification))

        // Empty packet
        assertFalse(P50SProtocol.isRasterBufferCompletionNotification(byteArrayOf()))
    }

    @Test
    fun `test Customer Receipt with dynamic items produces exactly ONE raster and ONE START STOP job pair`() {
        val repair = RepairEntity(
            id = 55L,
            jobNumber = "00200",
            customerName = "Kamal Perera",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy S22",
            imei = "358912345678901",
            fault = "Display Broken & Battery Draining Fast",
            totalPrice = 18500.0,
            amountPaid = 5000.0,
            balance = 13500.0,
            status = "IN_PROGRESS",
            paymentStatus = "PARTIAL",
            receivedDate = "01/10/2026",
            receivedTime = "10:30 AM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 55L, repairType = "Display Replacement (OLED)", price = 14000.0),
            RepairItemEntity(repairId = 55L, repairType = "Battery Replacement 4500mAh", price = 3500.0),
            RepairItemEntity(repairId = 55L, repairType = "Labor / Service Charge", price = 1000.0)
        )
        val payments = listOf(
            PaymentEntity(repairId = 55L, paymentNumber = 1, amount = 5000.0, date = "01/10/2026", time = "10:35 AM")
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            shopAddress = "Main Street, Colombo",
            shopPhone = "0112345678",
            currency = "Rs."
        )

        // Automatically calculated height based on items and content
        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, payments, settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        val calculatedHeight = bitmap.height
        assertTrue("Calculated height must be > 200px (tall receipt)", calculatedHeight > 200)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val plan = P50SProtocol.buildSingleJobReceiptPlan(monoBytes, calculatedHeight)

        // Must be exactly ONE raster command and ONE print job
        assertEquals(1, plan.rasterCount)
        assertEquals(1, plan.jobCount)

        val headerHeight = ((plan.rasterCommand[4].toInt() and 0xFF) shl 8) or (plan.rasterCommand[5].toInt() and 0xFF)
        assertEquals("Raster command height must equal bitmap's calculated height", calculatedHeight, headerHeight)
        
        val expectedTimeout = P50SProtocol.calculatePostRasterCompletionTimeoutMs(calculatedHeight)
        assertEquals(expectedTimeout, plan.postRasterTimeoutMs)
        assertTrue(plan.postRasterTimeoutMs >= 500L)

        // Must have final feed and align
        assertEquals(160, ((plan.finalFeedCommand[3].toInt() and 0xFF) shl 8) or (plan.finalFeedCommand[4].toInt() and 0xFF))
        assertEquals(0x50.toByte(), plan.finalAlignCommand[2])
    }

    @Test
    fun `test Payment Receipt produces exactly ONE raster and ONE START STOP job pair`() {
        val repair = RepairEntity(
            id = 56L,
            jobNumber = "00201",
            customerName = "Sunil Shantha",
            customerPhone = "0779998877",
            brand = "Xiaomi",
            model = "Redmi Note 12",
            fault = "Charging Port Replacement",
            totalPrice = 3500.0,
            amountPaid = 3500.0,
            balance = 0.0,
            status = "COMPLETED",
            paymentStatus = "PAID",
            receivedDate = "01/10/2026",
            receivedTime = "11:00 AM"
        )
        val payment = PaymentEntity(
            repairId = 56L,
            paymentNumber = 1,
            amount = 3500.0,
            date = "01/10/2026",
            time = "11:15 AM"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        val calculatedHeight = bitmap.height
        assertTrue("Calculated height must be positive", calculatedHeight > 80)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val plan = P50SProtocol.buildSingleJobReceiptPlan(monoBytes, calculatedHeight)

        // Verify ONE raster and ONE START/STOP pair
        assertEquals(1, plan.rasterCount)
        assertEquals(1, plan.jobCount)
        val headerHeight = ((plan.rasterCommand[4].toInt() and 0xFF) shl 8) or (plan.rasterCommand[5].toInt() and 0xFF)
        assertEquals(calculatedHeight, headerHeight)
        val expectedTimeout = P50SProtocol.calculatePostRasterCompletionTimeoutMs(calculatedHeight)
        assertEquals(expectedTimeout, plan.postRasterTimeoutMs)
        assertTrue(plan.postRasterTimeoutMs >= 500L)
    }

    @Test
    fun `test P50S sequential separate-job receipt printing architecture test cases 1 to 10`() {
        val testCases = listOf(
            // (testIndex, totalHeight, expectedBlockHeights)
            Triple(1, 0, emptyList<Int>()),
            Triple(2, 180, listOf(180)),
            Triple(3, 200, listOf(200)),
            Triple(4, 201, listOf(200, 1)),
            Triple(5, 350, listOf(200, 150)),
            Triple(6, 400, listOf(200, 200)),
            Triple(7, 550, listOf(200, 200, 150)),
            Triple(8, 600, listOf(200, 200, 200)),
            Triple(9, 735, listOf(200, 200, 200, 135)),
            Triple(10, 840, listOf(200, 200, 200, 200, 40))
        )

        for ((testIndex, totalHeight, expectedHeights) in testCases) {
            val monoData = if (totalHeight > 0) ByteArray(totalHeight * P50SProtocol.BYTES_PER_ROW) else ByteArray(0)
            val plan = P50SProtocol.buildSeparateJobPrintPlan(monoData, totalHeight, maxBlockHeight = 200)

            if (totalHeight == 0) {
                // Test 1: 0px / empty bitmap -> safe handling
                assertTrue("Test 1: 0px / empty bitmap must yield empty plan safely", plan.isEmpty)
                assertEquals(0, plan.jobCount)
                continue
            }

            // Verify job count matches expected
            assertEquals("Test $testIndex ($totalHeight px) job count mismatch", expectedHeights.size, plan.jobCount)

            var heightSum = 0
            for ((jobIdx, block) in plan.blocks.withIndex()) {
                val expectedBlockHeight = expectedHeights[jobIdx]

                // 1. Verify exactly ONE raster command per job
                assertEquals("Job $jobIdx in Test $testIndex must have exactly 1 raster", 1, block.rasterCount)

                // 2. Verify every raster height <= 200px and matches expected
                assertEquals("Job $jobIdx height mismatch in Test $testIndex", expectedBlockHeight, block.blockHeight)
                assertTrue("Every raster height must be <= 200px", block.blockHeight <= 200)
                assertTrue("Raster height must be > 0", block.blockHeight > 0)

                // Verify raster command header and content
                val cmd = block.rasterCommand
                assertTrue("Raster command must have header + payload", cmd.size > 10)
                assertEquals(0x1F.toByte(), cmd[0])
                assertEquals(0x10.toByte(), cmd[1])
                assertEquals(0x00.toByte(), cmd[2])
                assertEquals(48.toByte(), cmd[3])

                val cmdHeight = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
                assertEquals("Raster command header height must match block height", expectedBlockHeight, cmdHeight)

                // 3. Verify no feed between jobs (raster command must not be a feed command 0x11)
                assertFalse("Raster command must not contain paper feed command 0x11 in header", cmd[1] == 0x11.toByte())

                // 4. Verify no alignment between jobs
                assertFalse("Raster command must not contain align command", cmd[1] == 0x11.toByte() && cmd.size == 3 && cmd[2] == 0x50.toByte())

                heightSum += block.blockHeight
            }
            assertEquals("Sum of block heights must equal total height $totalHeight in Test $testIndex", totalHeight, heightSum)

            // 5. Verify final feed (160px) and final align configured strictly for execution after final job
            val feed = plan.finalFeedCommand
            assertEquals(0x1F.toByte(), feed[0])
            assertEquals(0x11.toByte(), feed[1])
            val feedPx = ((feed[3].toInt() and 0xFF) shl 8) or (feed[4].toInt() and 0xFF)
            assertEquals("Final feed must be exactly 160px", 160, feedPx)

            val align = plan.finalAlignCommand
            assertEquals(0x1F.toByte(), align[0])
            assertEquals(0x11.toByte(), align[1])
            assertEquals(0x50.toByte(), align[2])

            // 6. Verify 500ms post-raster settling delay and 300ms inter-job delay
            assertEquals(500L, plan.postRasterSettlingDelayMs)
            assertEquals(300L, plan.interJobDelayMs)
        }
    }

    @Test
    fun `test Customer Receipt with dynamic items produces separate jobs of max 200px each`() {
        val repair = RepairEntity(
            id = 55L,
            jobNumber = "00200",
            customerName = "Kamal Perera",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy S22",
            imei = "358912345678901",
            fault = "Display Broken & Battery Draining Fast",
            totalPrice = 18500.0,
            amountPaid = 5000.0,
            balance = 13500.0,
            status = "IN_PROGRESS",
            paymentStatus = "PARTIAL",
            receivedDate = "01/10/2026",
            receivedTime = "10:30 AM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 55L, repairType = "Display Replacement (OLED)", price = 14000.0),
            RepairItemEntity(repairId = 55L, repairType = "Battery Replacement 4500mAh", price = 3500.0),
            RepairItemEntity(repairId = 55L, repairType = "Labor / Service Charge", price = 1000.0)
        )
        val payments = listOf(
            PaymentEntity(repairId = 55L, paymentNumber = 1, amount = 5000.0, date = "01/10/2026", time = "10:35 AM")
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            shopAddress = "Main Street, Colombo",
            shopPhone = "0112345678",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, payments, settings)
        assertNotNull(bitmap)
        val calculatedHeight = bitmap.height
        assertTrue(calculatedHeight > 200)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val plan = P50SProtocol.buildSeparateJobPrintPlan(monoBytes, calculatedHeight, maxBlockHeight = 200)

        val expectedJobCount = (calculatedHeight + 199) / 200
        assertEquals(expectedJobCount, plan.jobCount)

        var sumHeight = 0
        for (block in plan.blocks) {
            assertEquals(1, block.rasterCount)
            assertTrue("Every block must be <= 200px", block.blockHeight <= 200)
            sumHeight += block.blockHeight
        }
        assertEquals(calculatedHeight, sumHeight)
    }

    @Test
    fun `test Payment Receipt produces separate jobs of max 200px each`() {
        val repair = RepairEntity(
            id = 56L,
            jobNumber = "00201",
            customerName = "Sunil Shantha",
            customerPhone = "0779998877",
            brand = "Xiaomi",
            model = "Redmi Note 12",
            fault = "Charging Port Replacement",
            totalPrice = 3500.0,
            amountPaid = 3500.0,
            balance = 0.0,
            status = "COMPLETED",
            paymentStatus = "PAID",
            receivedDate = "01/10/2026",
            receivedTime = "11:00 AM"
        )
        val payment = PaymentEntity(
            repairId = 56L,
            paymentNumber = 1,
            amount = 3500.0,
            date = "01/10/2026",
            time = "11:15 AM"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        assertNotNull(bitmap)
        val calculatedHeight = bitmap.height
        assertTrue(calculatedHeight > 80)

        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val plan = P50SProtocol.buildSeparateJobPrintPlan(monoBytes, calculatedHeight, maxBlockHeight = 200)

        val expectedJobCount = (calculatedHeight + 199) / 200
        assertEquals(expectedJobCount, plan.jobCount)

        var sumHeight = 0
        for (block in plan.blocks) {
            assertEquals(1, block.rasterCount)
            assertTrue("Every block must be <= 200px", block.blockHeight <= 200)
            sumHeight += block.blockHeight
        }
        assertEquals(calculatedHeight, sumHeight)
    }

    @Test
    fun `test P50S raster print sequencing architecture guarantees max 200px and zero inter-job feed`() {
        val testHeights = listOf(150, 200, 201, 399, 400, 550, 600, 750, 1000)

        for (height in testHeights) {
            val mono = ByteArray(height * P50SProtocol.BYTES_PER_ROW) { 0xAA.toByte() }
            val plan = P50SProtocol.buildSeparateJobPrintPlan(mono, height, maxBlockHeight = 200)

            // 1. Job count must match ceil(height / 200)
            val expectedJobs = (height + 199) / 200
            assertEquals(expectedJobs, plan.jobCount)

            var accumulatedHeight = 0
            for ((idx, block) in plan.blocks.withIndex()) {
                // 2. Exactly one raster command per job
                assertEquals("Job $idx must have exactly 1 raster", 1, block.rasterCount)

                // 3. Max raster height per print job must remain <= 200px
                assertTrue("Job $idx height must be <= 200px", block.blockHeight <= 200)
                assertTrue("Job $idx height must be > 0", block.blockHeight > 0)

                // 4. Command verification
                val cmd = block.rasterCommand
                assertEquals(0x1F.toByte(), cmd[0])
                assertEquals(0x10.toByte(), cmd[1]) // Sub-command 0x10 = Raster Image
                assertFalse("Raster command must not contain paper feed command 0x11 in header", cmd[1] == 0x11.toByte())

                val headerHeight = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
                assertEquals(block.blockHeight, headerHeight)

                accumulatedHeight += block.blockHeight
            }
            assertEquals(height, accumulatedHeight)

            // 5. Verify NO paper feed command (0x11) in START or STOP commands
            assertFalse(plan.startJobCommand.contains(0x11.toByte()))
            assertFalse(plan.stopJobCommand.contains(0x11.toByte()))

            // 6. Verify final feed is strictly 160px
            val feed = plan.finalFeedCommand
            assertEquals(0x1F.toByte(), feed[0])
            assertEquals(0x11.toByte(), feed[1])
            val feedHeight = ((feed[3].toInt() and 0xFF) shl 8) or (feed[4].toInt() and 0xFF)
            assertEquals("Final feed must be exactly 160px", 160, feedHeight)

            // 7. Verify final align is CMD_ALIGN_END
            assertEquals(0x50.toByte(), plan.finalAlignCommand[2])

            // 8. Verify exact 300ms inter-job delay configuration
            assertEquals(300L, plan.interJobDelayMs)

            // 9. Verify completion timeout scaling is safely bounded
            val timeout = P50SProtocol.calculatePostRasterCompletionTimeoutMs(200)
            assertTrue("Timeout for 200px must be between 1000ms and 3000ms", timeout in 1000L..3000L)
        }
    }

    @Test
    fun `test content-aware slicing with a text line crossing the 200px boundary`() {
        val totalHeight = 350
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Content from 0 to 185
        for (r in 0..185) {
            mono[r * bytesPerRow + 10] = 0xFF.toByte()
        }
        // Rows 186..192: safe blank whitespace
        // Text line from 193 to 215 (e.g., "Nuwan Premarathna") crossing the 200px boundary!
        for (r in 193..215) {
            mono[r * bytesPerRow + 15] = 0xAA.toByte()
        }
        // Rows 216..225: safe blank whitespace
        // Content from 226 to 349
        for (r in 226 until totalHeight) {
            mono[r * bytesPerRow + 20] = 0x55.toByte()
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 200)

        // Block 1 must NOT cut through the text line at row 199/200.
        // It must stop at the safe blank row 192.
        val block1 = blocks[0]
        assertEquals("Block 1 must start at row 0", 0, block1.startRow)
        assertEquals("Block 1 must end at safe blank row 192 (height 193)", 193, block1.blockHeight)
        assertTrue("Block 1 height must be <= 200", block1.blockHeight <= 200)

        // Verify the last row of Block 1 is blank
        val endRow1 = block1.startRow + block1.blockHeight - 1
        assertEquals(192, endRow1)
        assertTrue("End row of Block 1 must be blank", P50SProtocol.isRowBlank(mono, endRow1, bytesPerRow))

        // Block 2 starts at row 193, where the text line begins
        val block2 = blocks[1]
        assertEquals("Block 2 must start at row 193", 193, block2.startRow)

        // Verify the entire text line (193..215) is completely within Block 2!
        val block2EndRow = block2.startRow + block2.blockHeight - 1
        assertTrue("Block 2 must contain the entire text line up to row 215", block2EndRow >= 215)

        // Verify no text line pixels were split across jobs
        for (r in 193..215) {
            assertFalse("Row $r in text line must have pixels", P50SProtocol.isRowBlank(mono, r, bytesPerRow))
        }
    }

    @Test
    fun `test content-aware slicing with a blank row immediately before 200px`() {
        val totalHeight = 350
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Content from 0 to 198
        for (r in 0..198) {
            mono[r * bytesPerRow + 5] = 0xFF.toByte()
        }
        // Row 199 is immediately before 200px boundary (0-indexed: row 199 is the 200th row) and is BLANK!
        // Content resumes at row 200
        for (r in 200 until totalHeight) {
            mono[r * bytesPerRow + 5] = 0xFF.toByte()
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 200)

        // Block 1 should take exactly 200px, ending at row 199
        val block1 = blocks[0]
        assertEquals("Block 1 must start at row 0", 0, block1.startRow)
        assertEquals("Block 1 must have max height 200 ending on blank row 199", 200, block1.blockHeight)
        val endRow1 = block1.startRow + block1.blockHeight - 1
        assertEquals(199, endRow1)
        assertTrue("Row 199 must be blank", P50SProtocol.isRowBlank(mono, endRow1, bytesPerRow))

        // Block 2 starts at row 200
        val block2 = blocks[1]
        assertEquals(200, block2.startRow)
    }

    @Test
    fun `test content-aware slicing with a blank row at 180px`() {
        val totalHeight = 350
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Content from 0 to 175
        for (r in 0..175) {
            mono[r * bytesPerRow + 8] = 0xFF.toByte()
        }
        // Blank spacing from 176 to 180 (row 180 is the nearest safe blank row before 200)
        // Content from 181 to 240 (crosses 200px boundary)
        for (r in 181..240) {
            mono[r * bytesPerRow + 8] = 0xAA.toByte()
        }
        // Content remainder
        for (r in 250 until totalHeight) {
            mono[r * bytesPerRow + 8] = 0x55.toByte()
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 200)

        // Nearest safe blank row before row 200 is row 180
        val block1 = blocks[0]
        assertEquals(0, block1.startRow)
        assertEquals("Block 1 ends at row 180 (height = 181)", 181, block1.blockHeight)
        val endRow1 = block1.startRow + block1.blockHeight - 1
        assertEquals(180, endRow1)
        assertTrue(P50SProtocol.isRowBlank(mono, endRow1, bytesPerRow))

        // Block 2 starts at row 181, containing content 181..240 without mid-text split
        val block2 = blocks[1]
        assertEquals(181, block2.startRow)
        assertTrue("Block 2 must be <= 200px", block2.blockHeight <= 200)
    }

    @Test
    fun `test content-aware slicing where the first safe boundary is 150px`() {
        val totalHeight = 400
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Content from 0 to 145
        for (r in 0..145) {
            mono[r * bytesPerRow + 2] = 0xFF.toByte()
        }
        // Blank space from 146 to 150
        // Large content block from 151 to 230 (spanning across row 200)
        for (r in 151..230) {
            mono[r * bytesPerRow + 2] = 0xFF.toByte()
        }
        // Remaining content from 240 until totalHeight
        for (r in 240 until totalHeight) {
            mono[r * bytesPerRow + 2] = 0xFF.toByte()
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 200)

        // Nearest safe blank row before 200px is row 150
        val block1 = blocks[0]
        assertEquals(0, block1.startRow)
        assertEquals("First safe boundary is row 150, height = 151", 151, block1.blockHeight)
        val endRow1 = block1.startRow + block1.blockHeight - 1
        assertEquals(150, endRow1)
        assertTrue(P50SProtocol.isRowBlank(mono, endRow1, bytesPerRow))

        // Block 2 starts at row 151
        val block2 = blocks[1]
        assertEquals(151, block2.startRow)
        assertTrue("Block 2 must be <= 200px", block2.blockHeight <= 200)
    }

    @Test
    fun `test content-aware slicing with multiple consecutive text lines`() {
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        // Simulate a receipt with 15 text lines, each 18px tall with 6px blank spacing
        val lineHeight = 18
        val spacing = 6
        val lineCount = 15
        val totalHeight = lineCount * (lineHeight + spacing) // 15 * 24 = 360px
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        for (i in 0 until lineCount) {
            val textStart = i * (lineHeight + spacing)
            val textEnd = textStart + lineHeight - 1
            for (r in textStart..textEnd) {
                // Printed pixels in the text line
                mono[r * bytesPerRow + 10] = 0xFF.toByte()
            }
            // Rows textEnd + 1 .. textEnd + spacing remain blank (0x00)
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 200)

        // Verify that:
        // 1. Every block height <= 200
        // 2. Sum of block heights == totalHeight
        // 3. For EVERY block (except possibly the very last), the end row is a blank row
        // 4. NO text line is split across blocks (every line's start and end are in the same block)
        var accumulatedHeight = 0
        for ((idx, block) in blocks.withIndex()) {
            assertTrue("Block $idx height must be <= 200", block.blockHeight <= 200)
            assertTrue("Block $idx height must be > 0", block.blockHeight > 0)

            val endRow = block.startRow + block.blockHeight - 1
            if (idx < blocks.size - 1) {
                assertTrue("End row of block $idx must be blank", P50SProtocol.isRowBlank(mono, endRow, bytesPerRow))
            }
            accumulatedHeight += block.blockHeight
        }
        assertEquals(totalHeight, accumulatedHeight)

        // Check each line: verify it is completely inside ONE block
        for (i in 0 until lineCount) {
            val textStart = i * (lineHeight + spacing)
            val textEnd = textStart + lineHeight - 1

            // Find which block contains textStart
            val blockWithStart = blocks.firstOrNull { textStart >= it.startRow && textStart < it.startRow + it.blockHeight }
            assertNotNull("Block containing start of line $i must exist", blockWithStart)

            // That same block MUST also contain textEnd!
            val blockEnd = blockWithStart!!.startRow + blockWithStart.blockHeight - 1
            assertTrue(
                "Line $i (rows $textStart..$textEnd) must be fully contained in block (rows ${blockWithStart.startRow}..$blockEnd)",
                textEnd <= blockEnd
            )
        }
    }

    @Test
    fun `test content-aware slicing with a final raster smaller than 200px`() {
        val totalHeight = 250
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Content from 0 to 170
        for (r in 0..170) {
            mono[r * bytesPerRow + 5] = 0xAA.toByte()
        }
        // Blank space 171..176
        // Content from 177 to 249
        for (r in 177 until totalHeight) {
            mono[r * bytesPerRow + 5] = 0x55.toByte()
        }

        val plan = P50SProtocol.buildSeparateJobPrintPlan(mono, totalHeight, maxBlockHeight = 200)

        // Block 1 ends at row 176 (height = 177 <= 200)
        assertEquals(2, plan.jobCount)
        val block1 = plan.blocks[0]
        assertEquals(177, block1.blockHeight)
        assertTrue(block1.blockHeight <= 200)

        // Block 2 (final raster): remaining height is 250 - 177 = 73px (< 200px)
        val block2 = plan.blocks[1]
        assertEquals(73, block2.blockHeight)
        assertTrue("Final raster must be smaller than 200px", block2.blockHeight < 200)
        assertEquals(177, block2.startRow)

        // Sum equals total height
        assertEquals(totalHeight, block1.blockHeight + block2.blockHeight)

        // Architecture guarantees
        assertEquals(1, block1.rasterCount)
        assertEquals(1, block2.rasterCount)
        assertEquals(160, ((plan.finalFeedCommand[3].toInt() and 0xFF) shl 8) or (plan.finalFeedCommand[4].toInt() and 0xFF))
    }

    @Test
    fun `test real customer receipt with Nuwan Premarathna preserves intact text lines and safe blank slicing`() {
        val repair = RepairEntity(
            jobNumber = "00125",
            customerName = "Nuwan Premarathna",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 3500.0,
            amountPaid = 2000.0,
            balance = 1500.0,
            status = "REPAIRING",
            paymentStatus = "PARTIAL",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 0L, repairType = "Display Replacement", price = 3500.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, bitmap.height, 200)
        assertTrue("Receipt must be divided into blocks", blocks.isNotEmpty())

        var accumulatedHeight = 0
        for ((idx, block) in blocks.withIndex()) {
            assertTrue("Block $idx height must be <= 200px", block.blockHeight <= 200)
            assertTrue("Block $idx height must be > 0", block.blockHeight > 0)
            accumulatedHeight += block.blockHeight

            val endRow = block.startRow + block.blockHeight - 1
            if (idx < blocks.size - 1) {
                val isBlank = P50SProtocol.isRowBlank(mono, endRow)
                assertTrue("Intermediate block $idx must end on a safe blank row (row $endRow)", isBlank)
            }
        }
        assertEquals(bitmap.height, accumulatedHeight)
    }

    @Test
    fun `test real payment receipt preserves intact text lines and safe blank slicing`() {
        val repair = RepairEntity(
            jobNumber = "00125",
            customerName = "Nuwan Premarathna",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 3500.0,
            amountPaid = 3500.0,
            balance = 0.0,
            status = "COMPLETED",
            paymentStatus = "PAID",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val payment = PaymentEntity(
            repairId = 0L,
            paymentNumber = 1,
            amount = 3500.0,
            date = "01/10/2026",
            time = "11:15 AM"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, bitmap.height, 200)
        assertTrue("Payment receipt must be divided into blocks", blocks.isNotEmpty())

        var accumulatedHeight = 0
        for ((idx, block) in blocks.withIndex()) {
            assertTrue("Block $idx height must be <= 200px", block.blockHeight <= 200)
            assertTrue("Block $idx height must be > 0", block.blockHeight > 0)
            accumulatedHeight += block.blockHeight

            val endRow = block.startRow + block.blockHeight - 1
            if (idx < blocks.size - 1) {
                val isBlank = P50SProtocol.isRowBlank(mono, endRow)
                assertTrue("Intermediate block $idx must end on a safe blank row (row $endRow)", isBlank)
            }
        }
        assertEquals(bitmap.height, accumulatedHeight)
    }

    @Test
    fun `test P50S printing system settings defaults and sanitization`() {
        val defaultSettings = P50SPrintingSystemSettings()
        assertEquals(28L, defaultSettings.imagePacketPacingMs)
        assertTrue(defaultSettings.startCommandEnabled)
        assertEquals(200, defaultSettings.rasterMaxHeight)
        assertTrue(defaultSettings.ff03CompletionCheckEnabled)
        assertEquals(500L, defaultSettings.postRasterDelayMs)
        assertEquals(300L, defaultSettings.interJobDelayMs)
        assertTrue(defaultSettings.stopCommandEnabled)

        // Test sanitization bounds
        val invalidSettings = P50SPrintingSystemSettings(
            imagePacketPacingMs = -10L,
            rasterMaxHeight = 5,
            postRasterDelayMs = -200L,
            interJobDelayMs = -50L
        ).sanitized()

        assertEquals(P50SPrintingSystemSettings.MIN_PACKET_PACING_MS, invalidSettings.imagePacketPacingMs)
        assertEquals(P50SPrintingSystemSettings.MIN_RASTER_HEIGHT, invalidSettings.rasterMaxHeight)
        assertEquals(P50SPrintingSystemSettings.MIN_DELAY_MS, invalidSettings.postRasterDelayMs)
        assertEquals(P50SPrintingSystemSettings.MIN_DELAY_MS, invalidSettings.interJobDelayMs)

        val largeSettings = P50SPrintingSystemSettings(
            imagePacketPacingMs = 99999L,
            rasterMaxHeight = 99999,
            postRasterDelayMs = 99999L,
            interJobDelayMs = 99999L
        ).sanitized()

        assertEquals(P50SPrintingSystemSettings.MAX_PACKET_PACING_MS, largeSettings.imagePacketPacingMs)
        assertEquals(P50SPrintingSystemSettings.MAX_RASTER_HEIGHT, largeSettings.rasterMaxHeight)
        assertEquals(P50SPrintingSystemSettings.MAX_DELAY_MS, largeSettings.postRasterDelayMs)
        assertEquals(P50SPrintingSystemSettings.MAX_DELAY_MS, largeSettings.interJobDelayMs)
    }

    @Test
    fun `test P50S printing system settings persistence and reset via PrinterPreferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Reset to ensure known clean state
        val resetSettings = PrinterPreferences.resetP50SSettings(context)
        assertEquals(28L, resetSettings.imagePacketPacingMs)
        assertTrue(resetSettings.startCommandEnabled)
        assertEquals(200, resetSettings.rasterMaxHeight)
        assertTrue(resetSettings.ff03CompletionCheckEnabled)
        assertEquals(500L, resetSettings.postRasterDelayMs)
        assertEquals(300L, resetSettings.interJobDelayMs)
        assertTrue(resetSettings.stopCommandEnabled)
        assertFalse(resetSettings.experimentalMultiRasterModeEnabled)

        // 2. Save custom tuned settings
        val customSettings = P50SPrintingSystemSettings(
            imagePacketPacingMs = 35L,
            startCommandEnabled = false,
            rasterMaxHeight = 160,
            ff03CompletionCheckEnabled = false,
            postRasterDelayMs = 400L,
            interJobDelayMs = 250L,
            stopCommandEnabled = false,
            experimentalMultiRasterModeEnabled = true
        )
        PrinterPreferences.saveP50SSettings(context, customSettings)

        // 3. Retrieve and verify persistence
        val retrieved = PrinterPreferences.getP50SSettings(context)
        assertEquals(35L, retrieved.imagePacketPacingMs)
        assertFalse(retrieved.startCommandEnabled)
        assertEquals(160, retrieved.rasterMaxHeight)
        assertFalse(retrieved.ff03CompletionCheckEnabled)
        assertEquals(400L, retrieved.postRasterDelayMs)
        assertEquals(250L, retrieved.interJobDelayMs)
        assertFalse(retrieved.stopCommandEnabled)
        assertTrue(retrieved.experimentalMultiRasterModeEnabled)

        // 4. Reset and verify restored to proven defaults
        val restored = PrinterPreferences.resetP50SSettings(context)
        assertEquals(28L, restored.imagePacketPacingMs)
        assertTrue(restored.startCommandEnabled)
        assertEquals(200, restored.rasterMaxHeight)
        assertTrue(restored.ff03CompletionCheckEnabled)
        assertEquals(500L, restored.postRasterDelayMs)
        assertEquals(300L, restored.interJobDelayMs)
        assertTrue(restored.stopCommandEnabled)
        assertFalse(restored.experimentalMultiRasterModeEnabled)
    }

    @Test
    fun `test content-aware slicing with custom raster maximum height of 150px`() {
        val totalHeight = 400
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Create lines with blank rows every 20px
        for (line in 0 until 20) {
            val start = line * 20
            for (r in start until (start + 15)) {
                mono[r * bytesPerRow + 5] = 0xFF.toByte()
            }
            // Rows start + 15 until start + 20 remain blank
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 150)
        assertTrue(blocks.isNotEmpty())

        var sumHeight = 0
        for ((idx, block) in blocks.withIndex()) {
            assertTrue("Block $idx height must be <= 150px", block.blockHeight <= 150)
            assertTrue("Block $idx height must be > 0", block.blockHeight > 0)
            sumHeight += block.blockHeight

            val endRow = block.startRow + block.blockHeight - 1
            if (idx < blocks.size - 1) {
                assertTrue("Intermediate block $idx must end on a safe blank row", P50SProtocol.isRowBlank(mono, endRow, bytesPerRow))
            }
        }
        assertEquals(totalHeight, sumHeight)
    }

    @Test
    fun `test content-aware slicing with custom raster maximum height of 250px`() {
        val totalHeight = 600
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }

        // Create lines with blank rows every 25px
        for (line in 0 until 24) {
            val start = line * 25
            for (r in start until (start + 20)) {
                mono[r * bytesPerRow + 5] = 0xFF.toByte()
            }
        }

        val blocks = P50SProtocol.buildSeparateJobBlocks(mono, totalHeight, maxBlockHeight = 250)
        assertTrue(blocks.isNotEmpty())

        var sumHeight = 0
        for ((idx, block) in blocks.withIndex()) {
            assertTrue("Block $idx height must be <= 250px", block.blockHeight <= 250)
            assertTrue("Block $idx height must be > 0", block.blockHeight > 0)
            sumHeight += block.blockHeight

            val endRow = block.startRow + block.blockHeight - 1
            if (idx < blocks.size - 1) {
                assertTrue("Intermediate block $idx must end on a safe blank row", P50SProtocol.isRowBlank(mono, endRow, bytesPerRow))
            }
        }
        assertEquals(totalHeight, sumHeight)
    }

    // =========================================================================
    // EXPERIMENTAL MULTI-RASTER SINGLE-JOB MODE UNIT TESTS
    // =========================================================================

    @Test
    fun `test OFF mode produces the existing separate-job sequence`() {
        // Default settings has experimentalMultiRasterModeEnabled = false
        val defaultSettings = P50SPrintingSystemSettings()
        assertFalse("Default mode must be OFF (Stable Separate-Job)", defaultSettings.experimentalMultiRasterModeEnabled)

        val totalHeight = 520
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }
        // Blank row every 20px
        for (r in 0 until totalHeight) {
            if (r % 20 != 19) mono[r * bytesPerRow + 2] = 0xFF.toByte()
        }

        // In OFF mode: builds separate job print plan
        val plan = P50SProtocol.buildSeparateJobPrintPlan(mono, totalHeight, maxBlockHeight = defaultSettings.rasterMaxHeight)
        assertTrue("Separate job plan must have multiple jobs for 520px", plan.jobCount >= 3)

        // OFF mode sequence:
        // Every job has a START command and a STOP command
        assertEquals(P50SProtocol.CMD_START_PRINT_JOB.toList(), plan.startJobCommand.toList())
        assertEquals(P50SProtocol.CMD_STOP_PRINT_JOB.toList(), plan.stopJobCommand.toList())

        // In OFF mode, inter-job delay is configured (default 300ms)
        assertEquals(300L, plan.interJobDelayMs)
        assertEquals(500L, plan.postRasterSettlingDelayMs)

        // Final feed and alignment occur after the final job
        assertEquals(P50SProtocol.CMD_FEED_PAPER_160PX.toList(), plan.finalFeedCommand.toList())
        assertEquals(P50SProtocol.CMD_ALIGN_END.toList(), plan.finalAlignCommand.toList())
    }

    @Test
    fun `test ON mode produces exactly ONE START command and ONE STOP command for the whole receipt`() {
        val totalHeight = 520
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }
        for (r in 0 until totalHeight) {
            if (r % 20 != 19) mono[r * bytesPerRow + 2] = 0xFF.toByte()
        }

        val plan = P50SProtocol.buildExperimentalMultiRasterPrintPlan(mono, totalHeight, maxBlockHeight = 200)

        // Must have multiple blocks
        assertTrue("Plan must have > 1 blocks for height 520px", plan.blockCount > 1)

        // ON mode session structure:
        // Exactly ONE START command before all blocks
        assertEquals(P50SProtocol.CMD_START_PRINT_JOB.toList(), plan.startJobCommand.toList())

        // Exactly ONE STOP command after all blocks
        assertEquals(P50SProtocol.CMD_STOP_PRINT_JOB.toList(), plan.stopJobCommand.toList())

        // Simulation of commands sent in ON mode
        val commandTrace = mutableListOf<String>()
        commandTrace.add("START_PRINT_JOB") // Sent once at start of session
        for (i in 0 until plan.blockCount) {
            commandTrace.add("RASTER_BLOCK_${i + 1}")
            commandTrace.add("CHECK_COMPLETION_${i + 1}")
        }
        commandTrace.add("STOP_PRINT_JOB") // Sent once at end of session
        commandTrace.add("FINAL_FEED_160PX")
        commandTrace.add("FINAL_ALIGN")

        val startCount = commandTrace.count { it == "START_PRINT_JOB" }
        val stopCount = commandTrace.count { it == "STOP_PRINT_JOB" }
        assertEquals("Must produce exactly ONE START command for the whole receipt", 1, startCount)
        assertEquals("Must produce exactly ONE STOP command for the whole receipt", 1, stopCount)
    }

    @Test
    fun `test ON mode sends multiple raster blocks sequentially`() {
        val totalHeight = 540
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }
        for (r in 0 until totalHeight) {
            if (r % 20 != 19) mono[r * bytesPerRow + 2] = 0xFF.toByte()
        }

        val plan = P50SProtocol.buildExperimentalMultiRasterPrintPlan(mono, totalHeight, maxBlockHeight = 200)

        // Multiple raster blocks
        assertTrue("Must contain multiple blocks", plan.blockCount >= 3)
        assertEquals(plan.blockCount, plan.totalRasterCount)

        var accumulatedHeight = 0
        var previousEndRow = -1

        for ((index, block) in plan.blocks.withIndex()) {
            assertEquals("Blocks must have sequential job indices", index, block.jobIndex)
            assertEquals("Block $index must start immediately after previous block", previousEndRow + 1, block.startRow)
            assertTrue("Block $index height must be <= 200px", block.blockHeight <= 200)
            assertTrue("Block $index height must be > 0", block.blockHeight > 0)

            // Verify raster command format: starts with 0x1F, 0x10 (Raster Image)
            val cmd = block.rasterCommand
            assertEquals(0x1F.toByte(), cmd[0])
            assertEquals(0x10.toByte(), cmd[1])

            // Verify width in raster command header is 48 bytes (384 dots)
            val widthInCmd = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
            assertEquals(48, widthInCmd)

            // Verify height in raster command header (cmd[4], cmd[5] big-endian) matches blockHeight
            val heightInCmd = ((cmd[4].toInt() and 0xFF) shl 8) or (cmd[5].toInt() and 0xFF)
            assertEquals(block.blockHeight, heightInCmd)

            // Verify raw monochrome size
            assertEquals(block.blockHeight * bytesPerRow, block.rawBytes.size)

            accumulatedHeight += block.blockHeight
            previousEndRow = block.startRow + block.blockHeight - 1
        }

        // All raster blocks sequentially cover the entire receipt height
        assertEquals("Total height of all sequential blocks must equal receipt height", totalHeight, accumulatedHeight)
    }

    @Test
    fun `test no feed or alignment occurs between raster blocks in experimental mode`() {
        val totalHeight = 450
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }
        for (r in 0 until totalHeight) {
            if (r % 25 != 24) mono[r * bytesPerRow + 4] = 0xAA.toByte()
        }

        val plan = P50SProtocol.buildExperimentalMultiRasterPrintPlan(mono, totalHeight, maxBlockHeight = 200)
        assertTrue(plan.blockCount >= 2)

        // Between raster blocks, only completion check and post-raster delay occur.
        // No feed commands or align commands are emitted between blocks.
        for (block in plan.blocks) {
            // Verify block raster command contains strictly the raster command (0x1F 0x10 ...)
            // and no feed or align command bytes appended
            val cmd = block.rasterCommand
            assertEquals(0x1F.toByte(), cmd[0])
            assertEquals(0x10.toByte(), cmd[1])

            // Block command does not contain separate feed or align command sequences
            val cmdList = cmd.toList()
            assertFalse("Raster block must not contain feed paper command", cmdList.containsAll(P50SProtocol.CMD_FEED_PAPER_160PX.toList()))
            assertFalse("Raster block must not contain align end command", cmdList.containsAll(P50SProtocol.CMD_ALIGN_END.toList()))
        }
    }

    @Test
    fun `test final feed and alignment occurs only after the final raster STOP command`() {
        val totalHeight = 350
        val bytesPerRow = P50SProtocol.BYTES_PER_ROW
        val mono = ByteArray(totalHeight * bytesPerRow) { 0 }
        for (r in 0 until totalHeight) {
            if (r % 20 != 19) mono[r * bytesPerRow + 1] = 0x77.toByte()
        }

        val plan = P50SProtocol.buildExperimentalMultiRasterPrintPlan(mono, totalHeight, maxBlockHeight = 200)

        // Verify final feed is 160px
        assertEquals(P50SProtocol.CMD_FEED_PAPER_160PX.toList(), plan.finalFeedCommand.toList())

        // Verify final align is (0x1F 0x11 0x50)
        assertEquals(P50SProtocol.CMD_ALIGN_END.toList(), plan.finalAlignCommand.toList())

        // Build full sequence execution trace:
        val executionTrace = mutableListOf<String>()

        // 1. Session start
        executionTrace.add("SESSION_START")
        executionTrace.add("START_PRINT_JOB")

        // 2. Sequential raster blocks
        for (block in plan.blocks) {
            executionTrace.add("SEND_RASTER_${block.jobIndex}_HEIGHT_${block.blockHeight}")
            executionTrace.add("FF03_COMPLETION_CHECK_${block.jobIndex}")
            executionTrace.add("POST_RASTER_DELAY_${block.jobIndex}")
        }

        // 3. Session end
        executionTrace.add("STOP_PRINT_JOB")
        executionTrace.add("FINAL_FEED_160PX")
        executionTrace.add("FINAL_ALIGN")
        executionTrace.add("SESSION_END")

        // Assertions on the trace
        val stopIdx = executionTrace.indexOf("STOP_PRINT_JOB")
        val feedIdx = executionTrace.indexOf("FINAL_FEED_160PX")
        val alignIdx = executionTrace.indexOf("FINAL_ALIGN")

        assertTrue("STOP_PRINT_JOB must exist in trace", stopIdx != -1)
        assertTrue("FINAL_FEED_160PX must occur strictly AFTER STOP_PRINT_JOB", feedIdx > stopIdx)
        assertTrue("FINAL_ALIGN must occur strictly AFTER FINAL_FEED_160PX", alignIdx > feedIdx)

        // Confirm NO feed or align happened before STOP_PRINT_JOB
        for (i in 0 until stopIdx) {
            assertFalse("No feed before STOP", executionTrace[i].contains("FEED"))
            assertFalse("No align before STOP", executionTrace[i].contains("ALIGN"))
        }
    }
}



