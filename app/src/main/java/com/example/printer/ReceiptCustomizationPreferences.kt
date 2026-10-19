package com.example.printer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class ReceiptTextSize {
    SMALL, NORMAL, LARGE;

    companion object {
        fun fromString(value: String?): ReceiptTextSize {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class ReceiptAlignment {
    LEFT, CENTER, RIGHT;

    companion object {
        fun fromString(value: String?): ReceiptAlignment {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LEFT
        }
    }
}

enum class ReceiptDividerStyle {
    DASHED, SOLID, NONE;

    companion object {
        fun fromString(value: String?): ReceiptDividerStyle {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DASHED
        }
    }
}

enum class ReceiptLogoSize(val widthPx: Int, val displayName: String) {
    SMALL(100, "Small (100px)"),
    MEDIUM(160, "Medium (160px)"),
    LARGE(240, "Large (240px)");

    companion object {
        fun fromString(value: String?): ReceiptLogoSize {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class ReceiptElementType(val displayName: String) {
    TEXT("Custom Text"),
    DYNAMIC_FIELD("Dynamic Field"),
    IMAGE_LOGO("Logo / Image"),
    DIVIDER("Divider Line"),
    BLANK_SPACE("Blank Space")
}

/**
 * An individual free-form receipt element in the designer.
 * Supports complete customization: reordering, text, labels, dynamic data binding,
 * styling (font size, bold, alignment, spacing), divider styling, blank space height,
 * and device logo images.
 */
data class ReceiptElement(
    val id: String = UUID.randomUUID().toString(),
    val type: ReceiptElementType = ReceiptElementType.TEXT,
    val isVisible: Boolean = true,
    // Custom Text or custom display content
    val text: String = "",
    // Dynamic field tag (e.g., "{SHOP_NAME}", "{JOB_NO}", "{REPAIR_ITEMS}")
    val fieldTag: String = "",
    // Optional custom label prefix (e.g. "Job No: ", "Customer: ", "Device: ")
    val customLabel: String = "",
    // Font and typography formatting
    val fontSize: ReceiptTextSize = ReceiptTextSize.NORMAL,
    val isBold: Boolean = false,
    val alignment: ReceiptAlignment = ReceiptAlignment.LEFT,
    val spacingBottom: Int = 4, // Spacing in pixels below this element
    // Divider configuration
    val dividerStyle: ReceiptDividerStyle = ReceiptDividerStyle.DASHED,
    // Blank space configuration
    val spaceHeight: Int = 10,
    // Logo configuration
    val logoPath: String = "",
    val logoSize: ReceiptLogoSize = ReceiptLogoSize.MEDIUM
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name)
        json.put("isVisible", isVisible)
        json.put("text", text)
        json.put("fieldTag", fieldTag)
        json.put("customLabel", customLabel)
        json.put("fontSize", fontSize.name)
        json.put("isBold", isBold)
        json.put("alignment", alignment.name)
        json.put("spacingBottom", spacingBottom)
        json.put("dividerStyle", dividerStyle.name)
        json.put("spaceHeight", spaceHeight)
        json.put("logoPath", logoPath)
        json.put("logoSize", logoSize.name)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ReceiptElement {
            val typeStr = json.optString("type", "TEXT")
            val parsedType = try {
                ReceiptElementType.valueOf(typeStr)
            } catch (_: Exception) {
                ReceiptElementType.TEXT
            }

            return ReceiptElement(
                id = json.optString("id", UUID.randomUUID().toString()),
                type = parsedType,
                isVisible = json.optBoolean("isVisible", true),
                text = json.optString("text", ""),
                fieldTag = json.optString("fieldTag", ""),
                customLabel = json.optString("customLabel", ""),
                fontSize = ReceiptTextSize.fromString(json.optString("fontSize", "NORMAL")),
                isBold = json.optBoolean("isBold", false),
                alignment = ReceiptAlignment.fromString(json.optString("alignment", "LEFT")),
                spacingBottom = json.optInt("spacingBottom", 4),
                dividerStyle = ReceiptDividerStyle.fromString(json.optString("dividerStyle", "DASHED")),
                spaceHeight = json.optInt("spaceHeight", 10),
                logoPath = json.optString("logoPath", ""),
                logoSize = ReceiptLogoSize.fromString(json.optString("logoSize", "MEDIUM"))
            )
        }
    }
}

/**
 * Free-form receipt template consisting of an ordered list of elements.
 */
data class ReceiptTemplate(
    val templateType: String = "CUSTOMER", // "CUSTOMER" or "PAYMENT"
    val elements: List<ReceiptElement> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("templateType", templateType)
        val arr = JSONArray()
        for (el in elements) {
            arr.put(el.toJson())
        }
        root.put("elements", arr)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?, defaultTemplate: ReceiptTemplate): ReceiptTemplate {
            if (jsonStr.isNullOrBlank()) return defaultTemplate
            return try {
                val root = JSONObject(jsonStr)
                val type = root.optString("templateType", defaultTemplate.templateType)
                val arr = root.optJSONArray("elements") ?: return defaultTemplate
                val list = mutableListOf<ReceiptElement>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(ReceiptElement.fromJson(obj))
                }
                ReceiptTemplate(templateType = type, elements = list)
            } catch (_: Exception) {
                defaultTemplate
            }
        }
    }
}

/**
 * Default templates and available dynamic field specifications.
 */
object DefaultTemplates {

    val CUSTOMER_FIELDS = listOf(
        "{SHOP_NAME}" to "Shop Business Name",
        "{SUBTITLE}" to "Subtitle / Tagline",
        "{ADDRESS}" to "Shop Physical Address",
        "{PHONE}" to "Shop Telephone Number",
        "{WHATSAPP}" to "WhatsApp Number",
        "{JOB_NO}" to "Job Number (#0001)",
        "{DATE}" to "Job Received Date",
        "{TIME}" to "Job Received Time",
        "{CUSTOMER_NAME}" to "Customer Name",
        "{CUSTOMER_PHONE}" to "Customer Phone",
        "{BRAND}" to "Device Brand",
        "{MODEL}" to "Device Model",
        "{IMEI}" to "Device IMEI Number",
        "{ACCESSORIES}" to "Accessories Left",
        "{FAULT}" to "Fault / Complaint",
        "{REPAIR_ITEMS}" to "Dynamic Repair Items List",
        "{TOTAL}" to "Total Repair Cost",
        "{PAID}" to "Advance Paid Amount",
        "{BALANCE}" to "Remaining Balance Due",
        "{PAYMENT_STATUS}" to "Payment Status (PAID / DUE)"
    )

    val PAYMENT_FIELDS = listOf(
        "{SHOP_NAME}" to "Shop Business Name",
        "{SUBTITLE}" to "Subtitle / Tagline",
        "{ADDRESS}" to "Shop Physical Address",
        "{PHONE}" to "Shop Telephone Number",
        "{WHATSAPP}" to "WhatsApp Number",
        "{JOB_NO}" to "Job Number (#0001)",
        "{DATE}" to "Payment Date",
        "{TIME}" to "Payment Time",
        "{CUSTOMER_NAME}" to "Customer Name",
        "{CUSTOMER_PHONE}" to "Customer Phone",
        "{BRAND}" to "Device Brand",
        "{MODEL}" to "Device Model",
        "{PAYMENT_AMOUNT}" to "Payment Amount Received",
        "{PREVIOUS_PAID}" to "Previously Paid Amount",
        "{TOTAL}" to "Total Repair Cost",
        "{TOTAL_PAID}" to "Total Amount Paid to Date",
        "{BALANCE}" to "Remaining Balance Due",
        "{PAYMENT_STATUS}" to "Payment Status (PAID / DUE)",
        "{PAYMENT_DATE}" to "Transaction Date",
        "{PAYMENT_TIME}" to "Transaction Time"
    )

    fun createCustomerDefaultTemplate(): ReceiptTemplate {
        val elements = listOf(
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{SHOP_NAME}", fontSize = ReceiptTextSize.LARGE, isBold = true, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{SUBTITLE}", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{PHONE}", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{ADDRESS}", isVisible = false, fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{WHATSAPP}", isVisible = false, fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 8),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{JOB_NO}", customLabel = "Job No: ", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{DATE}", customLabel = "Date: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{CUSTOMER_NAME}", customLabel = "Customer: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{CUSTOMER_PHONE}", customLabel = "Phone: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{BRAND}", customLabel = "Brand: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{MODEL}", customLabel = "Model: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{IMEI}", isVisible = false, customLabel = "IMEI: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{ACCESSORIES}", isVisible = false, customLabel = "Accessories: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{FAULT}", isVisible = false, customLabel = "Complaint: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{REPAIR_ITEMS}", customLabel = "Repair:", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 6),
            ReceiptElement(type = ReceiptElementType.DIVIDER, dividerStyle = ReceiptDividerStyle.DASHED, spacingBottom = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{TOTAL}", customLabel = "TOTAL:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{PAID}", customLabel = "PAID:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{BALANCE}", customLabel = "BALANCE:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{PAYMENT_STATUS}", isVisible = false, customLabel = "Payment Status: ", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 4),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 10),
            ReceiptElement(type = ReceiptElementType.TEXT, text = "Thank You!\nUDM MOBILE REPAIR", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.CENTER, spacingBottom = 6)
        )
        return ReceiptTemplate(templateType = "CUSTOMER", elements = elements)
    }

    fun createPaymentDefaultTemplate(): ReceiptTemplate {
        val elements = listOf(
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{SHOP_NAME}", fontSize = ReceiptTextSize.LARGE, isBold = true, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{SUBTITLE}", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{PHONE}", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.CENTER, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 6),
            ReceiptElement(type = ReceiptElementType.TEXT, text = "PAYMENT RECEIPT", fontSize = ReceiptTextSize.LARGE, isBold = true, alignment = ReceiptAlignment.CENTER, spacingBottom = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{JOB_NO}", customLabel = "Job No: ", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{DATE}", customLabel = "Date: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{CUSTOMER_NAME}", customLabel = "Customer: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{CUSTOMER_PHONE}", customLabel = "Phone: ", fontSize = ReceiptTextSize.NORMAL, isBold = false, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{PAYMENT_AMOUNT}", customLabel = "Payment Received:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 6),
            ReceiptElement(type = ReceiptElementType.DIVIDER, dividerStyle = ReceiptDividerStyle.DASHED, spacingBottom = 6),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{TOTAL}", customLabel = "Total:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{TOTAL_PAID}", customLabel = "Paid:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{BALANCE}", customLabel = "Balance:", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 3),
            ReceiptElement(type = ReceiptElementType.DYNAMIC_FIELD, fieldTag = "{PAYMENT_STATUS}", customLabel = "Payment Status: ", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.LEFT, spacingBottom = 4),
            ReceiptElement(type = ReceiptElementType.BLANK_SPACE, spaceHeight = 10),
            ReceiptElement(type = ReceiptElementType.TEXT, text = "Thank You!\nUDM MOBILE REPAIR", fontSize = ReceiptTextSize.NORMAL, isBold = true, alignment = ReceiptAlignment.CENTER, spacingBottom = 6)
        )
        return ReceiptTemplate(templateType = "PAYMENT", elements = elements)
    }
}

/**
 * Customer Receipt configuration model (legacy/bridge compatibility).
 */
data class CustomerReceiptConfig(
    // Shop information
    val shopName: String = "UDM MOBILE REPAIR",
    val subtitle: String = "Mobile Phone Repair",
    val phone: String = "07XXXXXXXX",
    val address: String = "",
    val whatsapp: String = "",
    val footer: String = "Thank You!\nUDM MOBILE REPAIR",

    // Visibility toggles
    val showShopName: Boolean = true,
    val showSubtitle: Boolean = true,
    val showPhone: Boolean = true,
    val showAddress: Boolean = false,
    val showDateTime: Boolean = true,
    val showJobNumber: Boolean = true,
    val showCustomerName: Boolean = true,
    val showCustomerPhone: Boolean = true,
    val showBrand: Boolean = true,
    val showModel: Boolean = true,
    val showImei: Boolean = false,
    val showComplaint: Boolean = false,
    val showRepairItems: Boolean = true,
    val showTotal: Boolean = true,
    val showPaid: Boolean = true,
    val showBalance: Boolean = true,
    val showPaymentStatus: Boolean = false,
    val showFooter: Boolean = true,

    // Text size
    val textSize: ReceiptTextSize = ReceiptTextSize.NORMAL,

    // Bold on/off
    val boldShopName: Boolean = true,
    val boldJobNumber: Boolean = true,
    val boldTotal: Boolean = true,
    val boldPaid: Boolean = true,
    val boldBalance: Boolean = true,

    // Alignment
    val headerAlignment: ReceiptAlignment = ReceiptAlignment.CENTER,
    val bodyAlignment: ReceiptAlignment = ReceiptAlignment.LEFT,
    val footerAlignment: ReceiptAlignment = ReceiptAlignment.CENTER,

    // Divider
    val dividerStyle: ReceiptDividerStyle = ReceiptDividerStyle.DASHED
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("shopName", shopName)
        json.put("subtitle", subtitle)
        json.put("phone", phone)
        json.put("address", address)
        json.put("whatsapp", whatsapp)
        json.put("footer", footer)

        json.put("showShopName", showShopName)
        json.put("showSubtitle", showSubtitle)
        json.put("showPhone", showPhone)
        json.put("showAddress", showAddress)
        json.put("showDateTime", showDateTime)
        json.put("showJobNumber", showJobNumber)
        json.put("showCustomerName", showCustomerName)
        json.put("showCustomerPhone", showCustomerPhone)
        json.put("showBrand", showBrand)
        json.put("showModel", showModel)
        json.put("showImei", showImei)
        json.put("showComplaint", showComplaint)
        json.put("showRepairItems", showRepairItems)
        json.put("showTotal", showTotal)
        json.put("showPaid", showPaid)
        json.put("showBalance", showBalance)
        json.put("showPaymentStatus", showPaymentStatus)
        json.put("showFooter", showFooter)

        json.put("textSize", textSize.name)

        json.put("boldShopName", boldShopName)
        json.put("boldJobNumber", boldJobNumber)
        json.put("boldTotal", boldTotal)
        json.put("boldPaid", boldPaid)
        json.put("boldBalance", boldBalance)

        json.put("headerAlignment", headerAlignment.name)
        json.put("bodyAlignment", bodyAlignment.name)
        json.put("footerAlignment", footerAlignment.name)

        json.put("dividerStyle", dividerStyle.name)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?): CustomerReceiptConfig {
            if (jsonStr.isNullOrBlank()) return CustomerReceiptConfig()
            return try {
                val json = JSONObject(jsonStr)
                CustomerReceiptConfig(
                    shopName = json.optString("shopName", "UDM MOBILE REPAIR"),
                    subtitle = json.optString("subtitle", "Mobile Phone Repair"),
                    phone = json.optString("phone", "07XXXXXXXX"),
                    address = json.optString("address", ""),
                    whatsapp = json.optString("whatsapp", ""),
                    footer = json.optString("footer", "Thank You!\nUDM MOBILE REPAIR"),

                    showShopName = json.optBoolean("showShopName", true),
                    showSubtitle = json.optBoolean("showSubtitle", true),
                    showPhone = json.optBoolean("showPhone", true),
                    showAddress = json.optBoolean("showAddress", false),
                    showDateTime = json.optBoolean("showDateTime", true),
                    showJobNumber = json.optBoolean("showJobNumber", true),
                    showCustomerName = json.optBoolean("showCustomerName", true),
                    showCustomerPhone = json.optBoolean("showCustomerPhone", true),
                    showBrand = json.optBoolean("showBrand", true),
                    showModel = json.optBoolean("showModel", true),
                    showImei = json.optBoolean("showImei", false),
                    showComplaint = json.optBoolean("showComplaint", false),
                    showRepairItems = json.optBoolean("showRepairItems", true),
                    showTotal = json.optBoolean("showTotal", true),
                    showPaid = json.optBoolean("showPaid", true),
                    showBalance = json.optBoolean("showBalance", true),
                    showPaymentStatus = json.optBoolean("showPaymentStatus", false),
                    showFooter = json.optBoolean("showFooter", true),

                    textSize = ReceiptTextSize.fromString(json.optString("textSize", "NORMAL")),

                    boldShopName = json.optBoolean("boldShopName", true),
                    boldJobNumber = json.optBoolean("boldJobNumber", true),
                    boldTotal = json.optBoolean("boldTotal", true),
                    boldPaid = json.optBoolean("boldPaid", true),
                    boldBalance = json.optBoolean("boldBalance", true),

                    headerAlignment = ReceiptAlignment.fromString(json.optString("headerAlignment", "CENTER")),
                    bodyAlignment = ReceiptAlignment.fromString(json.optString("bodyAlignment", "LEFT")),
                    footerAlignment = ReceiptAlignment.fromString(json.optString("footerAlignment", "CENTER")),

                    dividerStyle = ReceiptDividerStyle.fromString(json.optString("dividerStyle", "DASHED"))
                )
            } catch (_: Exception) {
                CustomerReceiptConfig()
            }
        }
    }
}

/**
 * Payment Receipt configuration model (legacy/bridge compatibility).
 */
data class PaymentReceiptConfig(
    // Shop information
    val shopName: String = "UDM MOBILE REPAIR",
    val subtitle: String = "Mobile Phone Repair",
    val phone: String = "07XXXXXXXX",
    val address: String = "",
    val whatsapp: String = "",
    val receiptTitle: String = "PAYMENT RECEIPT",
    val footer: String = "Thank You!\nUDM MOBILE REPAIR",

    // Visibility toggles
    val showShopName: Boolean = true,
    val showSubtitle: Boolean = true,
    val showPhone: Boolean = true,
    val showAddress: Boolean = false,
    val showDateTime: Boolean = true,
    val showReceiptTitle: Boolean = true,
    val showJobNumber: Boolean = true,
    val showCustomerName: Boolean = true,
    val showCustomerPhone: Boolean = true,
    val showPaymentReceived: Boolean = true,
    val showTotal: Boolean = true,
    val showPaid: Boolean = true,
    val showBalance: Boolean = true,
    val showPaymentStatus: Boolean = true,
    val showFooter: Boolean = true,

    // Text size
    val textSize: ReceiptTextSize = ReceiptTextSize.NORMAL,

    // Bold on/off
    val boldShopName: Boolean = true,
    val boldReceiptTitle: Boolean = true,
    val boldJobNumber: Boolean = true,
    val boldPaymentReceived: Boolean = true,
    val boldTotal: Boolean = true,
    val boldPaid: Boolean = true,
    val boldBalance: Boolean = true,

    // Alignment
    val headerAlignment: ReceiptAlignment = ReceiptAlignment.CENTER,
    val bodyAlignment: ReceiptAlignment = ReceiptAlignment.LEFT,
    val footerAlignment: ReceiptAlignment = ReceiptAlignment.CENTER,

    // Divider
    val dividerStyle: ReceiptDividerStyle = ReceiptDividerStyle.DASHED
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("shopName", shopName)
        json.put("subtitle", subtitle)
        json.put("phone", phone)
        json.put("address", address)
        json.put("whatsapp", whatsapp)
        json.put("receiptTitle", receiptTitle)
        json.put("footer", footer)

        json.put("showShopName", showShopName)
        json.put("showSubtitle", showSubtitle)
        json.put("showPhone", showPhone)
        json.put("showAddress", showAddress)
        json.put("showDateTime", showDateTime)
        json.put("showReceiptTitle", showReceiptTitle)
        json.put("showJobNumber", showJobNumber)
        json.put("showCustomerName", showCustomerName)
        json.put("showCustomerPhone", showCustomerPhone)
        json.put("showPaymentReceived", showPaymentReceived)
        json.put("showTotal", showTotal)
        json.put("showPaid", showPaid)
        json.put("showBalance", showBalance)
        json.put("showPaymentStatus", showPaymentStatus)
        json.put("showFooter", showFooter)

        json.put("textSize", textSize.name)

        json.put("boldShopName", boldShopName)
        json.put("boldReceiptTitle", boldReceiptTitle)
        json.put("boldJobNumber", boldJobNumber)
        json.put("boldPaymentReceived", boldPaymentReceived)
        json.put("boldTotal", boldTotal)
        json.put("boldPaid", boldPaid)
        json.put("boldBalance", boldBalance)

        json.put("headerAlignment", headerAlignment.name)
        json.put("bodyAlignment", bodyAlignment.name)
        json.put("footerAlignment", footerAlignment.name)

        json.put("dividerStyle", dividerStyle.name)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?): PaymentReceiptConfig {
            if (jsonStr.isNullOrBlank()) return PaymentReceiptConfig()
            return try {
                val json = JSONObject(jsonStr)
                PaymentReceiptConfig(
                    shopName = json.optString("shopName", "UDM MOBILE REPAIR"),
                    subtitle = json.optString("subtitle", "Mobile Phone Repair"),
                    phone = json.optString("phone", "07XXXXXXXX"),
                    address = json.optString("address", ""),
                    whatsapp = json.optString("whatsapp", ""),
                    receiptTitle = json.optString("receiptTitle", "PAYMENT RECEIPT"),
                    footer = json.optString("footer", "Thank You!\nUDM MOBILE REPAIR"),

                    showShopName = json.optBoolean("showShopName", true),
                    showSubtitle = json.optBoolean("showSubtitle", true),
                    showPhone = json.optBoolean("showPhone", true),
                    showAddress = json.optBoolean("showAddress", false),
                    showDateTime = json.optBoolean("showDateTime", true),
                    showReceiptTitle = json.optBoolean("showReceiptTitle", true),
                    showJobNumber = json.optBoolean("showJobNumber", true),
                    showCustomerName = json.optBoolean("showCustomerName", true),
                    showCustomerPhone = json.optBoolean("showCustomerPhone", true),
                    showPaymentReceived = json.optBoolean("showPaymentReceived", true),
                    showTotal = json.optBoolean("showTotal", true),
                    showPaid = json.optBoolean("showPaid", true),
                    showBalance = json.optBoolean("showBalance", true),
                    showPaymentStatus = json.optBoolean("showPaymentStatus", true),
                    showFooter = json.optBoolean("showFooter", true),

                    textSize = ReceiptTextSize.fromString(json.optString("textSize", "NORMAL")),

                    boldShopName = json.optBoolean("boldShopName", true),
                    boldReceiptTitle = json.optBoolean("boldReceiptTitle", true),
                    boldJobNumber = json.optBoolean("boldJobNumber", true),
                    boldPaymentReceived = json.optBoolean("boldPaymentReceived", true),
                    boldTotal = json.optBoolean("boldTotal", true),
                    boldPaid = json.optBoolean("boldPaid", true),
                    boldBalance = json.optBoolean("boldBalance", true),

                    headerAlignment = ReceiptAlignment.fromString(json.optString("headerAlignment", "CENTER")),
                    bodyAlignment = ReceiptAlignment.fromString(json.optString("bodyAlignment", "LEFT")),
                    footerAlignment = ReceiptAlignment.fromString(json.optString("footerAlignment", "CENTER")),

                    dividerStyle = ReceiptDividerStyle.fromString(json.optString("dividerStyle", "DASHED"))
                )
            } catch (_: Exception) {
                PaymentReceiptConfig()
            }
        }
    }
}

/**
 * Local persistent store for receipt customization templates and settings.
 * Ensures complete independence between Customer Receipt and Payment Receipt.
 * Settings survive app close, app restart, and phone reboot.
 */
object ReceiptPreferences {
    private const val PREFS_NAME = "udm_receipt_customization_prefs"
    private const val KEY_CUSTOMER_RECEIPT = "customer_receipt_config_json"
    private const val KEY_PAYMENT_RECEIPT = "payment_receipt_config_json"

    // V2 Free-form templates
    private const val KEY_CUSTOMER_TEMPLATE = "customer_receipt_template_v2"
    private const val KEY_PAYMENT_TEMPLATE = "payment_receipt_template_v2"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ================= CUSTOMER TEMPLATE =================

    fun getCustomerTemplate(context: Context): ReceiptTemplate {
        val json = getPrefs(context).getString(KEY_CUSTOMER_TEMPLATE, null)
        return ReceiptTemplate.fromJson(json, DefaultTemplates.createCustomerDefaultTemplate())
    }

    fun saveCustomerTemplate(context: Context, template: ReceiptTemplate) {
        getPrefs(context).edit()
            .putString(KEY_CUSTOMER_TEMPLATE, template.toJson())
            .apply()
    }

    fun resetCustomerTemplate(context: Context): ReceiptTemplate {
        getPrefs(context).edit()
            .remove(KEY_CUSTOMER_TEMPLATE)
            .apply()
        return DefaultTemplates.createCustomerDefaultTemplate()
    }

    // ================= PAYMENT TEMPLATE =================

    fun getPaymentTemplate(context: Context): ReceiptTemplate {
        val json = getPrefs(context).getString(KEY_PAYMENT_TEMPLATE, null)
        return ReceiptTemplate.fromJson(json, DefaultTemplates.createPaymentDefaultTemplate())
    }

    fun savePaymentTemplate(context: Context, template: ReceiptTemplate) {
        getPrefs(context).edit()
            .putString(KEY_PAYMENT_TEMPLATE, template.toJson())
            .apply()
    }

    fun resetPaymentTemplate(context: Context): ReceiptTemplate {
        getPrefs(context).edit()
            .remove(KEY_PAYMENT_TEMPLATE)
            .apply()
        return DefaultTemplates.createPaymentDefaultTemplate()
    }

    // ================= LOGO STORAGE =================

    fun saveLogoFromUri(context: Context, uri: Uri, prefix: String = "receipt_logo"): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val logoDir = File(context.filesDir, "receipt_logos").apply { if (!exists()) mkdirs() }
            val targetFile = File(logoDir, "${prefix}_${System.currentTimeMillis()}.png")
            targetFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteLogoFile(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun saveLogoBytes(context: Context, fileName: String, bytes: ByteArray): String? {
        return try {
            val logoDir = File(context.filesDir, "receipt_logos").apply { if (!exists()) mkdirs() }
            val targetFile = File(logoDir, fileName)
            targetFile.writeBytes(bytes)
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun getLogoBytes(path: String): ByteArray? {
        if (path.isBlank()) return null
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) file.readBytes() else null
        } catch (_: Exception) {
            null
        }
    }

    // ================= LEGACY CONFIG BACKWARDS COMPATIBILITY =================

    fun getCustomerReceiptConfig(context: Context): CustomerReceiptConfig {
        val json = getPrefs(context).getString(KEY_CUSTOMER_RECEIPT, null)
        return CustomerReceiptConfig.fromJson(json)
    }

    fun saveCustomerReceiptConfig(context: Context, config: CustomerReceiptConfig) {
        getPrefs(context).edit()
            .putString(KEY_CUSTOMER_RECEIPT, config.toJson())
            .apply()
    }

    fun resetCustomerReceiptConfig(context: Context): CustomerReceiptConfig {
        getPrefs(context).edit()
            .remove(KEY_CUSTOMER_RECEIPT)
            .apply()
        return CustomerReceiptConfig()
    }

    fun getPaymentReceiptConfig(context: Context): PaymentReceiptConfig {
        val json = getPrefs(context).getString(KEY_PAYMENT_RECEIPT, null)
        return PaymentReceiptConfig.fromJson(json)
    }

    fun savePaymentReceiptConfig(context: Context, config: PaymentReceiptConfig) {
        getPrefs(context).edit()
            .putString(KEY_PAYMENT_RECEIPT, config.toJson())
            .apply()
    }

    fun resetPaymentReceiptConfig(context: Context): PaymentReceiptConfig {
        getPrefs(context).edit()
            .remove(KEY_PAYMENT_RECEIPT)
            .apply()
        return PaymentReceiptConfig()
    }
}

/**
 * Robust Base64 helper supporting Android framework runtime and JVM tests.
 */
object Base64Helper {
    fun encode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}

/**
 * Logo backup item containing file name and Base64 encoded PNG/JPG image data.
 */
data class ReceiptLogoBackup(
    val fileName: String,
    val base64Data: String
)

/**
 * Comprehensive Receipt Customization Backup model.
 * Contains all customer and payment receipt templates, formatting settings,
 * paper width, P50S printer settings, and custom logo image data.
 */
data class ReceiptBackupData(
    val customerTemplate: ReceiptTemplate? = null,
    val paymentTemplate: ReceiptTemplate? = null,
    val customerConfig: CustomerReceiptConfig? = null,
    val paymentConfig: PaymentReceiptConfig? = null,
    val paperWidthDots: Int = 384,
    val autoPrintCustomerReceipt: Boolean = false,
    val customerReceiptDiagnosticMode: Boolean = false,
    val p50sSettings: P50SPrintingSystemSettings? = null,
    val logos: List<ReceiptLogoBackup> = emptyList()
)

