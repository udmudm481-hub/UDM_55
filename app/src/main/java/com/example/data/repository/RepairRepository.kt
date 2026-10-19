package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.BrandEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.FaultEntity
import com.example.data.entity.ModelEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PriceEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import com.example.data.entity.RepairTypeEntity
import com.example.data.entity.ScannerLearningEntity
import com.example.data.entity.StatusHistoryEntity
import com.example.data.entity.StockItemEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.entity.StockRepairConnectionEntity
import com.example.data.entity.UniversalRepairTypeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import com.example.printer.Base64Helper
import com.example.printer.PrinterPreferences
import com.example.printer.ReceiptBackupData
import com.example.printer.ReceiptElementType
import com.example.printer.ReceiptLogoBackup
import com.example.printer.ReceiptPreferences

sealed class SmsSendResult {
    abstract val isSuccess: Boolean
    abstract val message: String

    data class SentOrQueued(
        val recipient: String,
        override val message: String = "Ready SMS initiated and queued for $recipient"
    ) : SmsSendResult() {
        override val isSuccess: Boolean = true
    }

    data class PermissionDenied(
        override val message: String = "SMS permission not granted. Please allow SMS permission in Android settings."
    ) : SmsSendResult() {
        override val isSuccess: Boolean = false
    }

    data class Failed(
        val error: String,
        override val message: String = "Failed to send SMS: $error"
    ) : SmsSendResult() {
        override val isSuccess: Boolean = false
    }

    data class DisabledInSettings(
        override val message: String = "Automatic Ready SMS is disabled in Settings."
    ) : SmsSendResult() {
        override val isSuccess: Boolean = false
    }
}

data class SmsEvent(
    val repairId: Long,
    val jobNumber: String,
    val customerPhone: String,
    val result: SmsSendResult,
    val isAuto: Boolean,
    val message: String
)

sealed class UpdateCustomerResult {
    object Success : UpdateCustomerResult()
    data class PhoneConflict(val existingCustomerName: String) : UpdateCustomerResult()
    data class ValidationError(val message: String) : UpdateCustomerResult()
}

data class InsufficientStockInfo(
    val stockItem: StockItemEntity,
    val available: Int,
    val required: Int,
    val repairItemType: String
) {
    val shortage: Int get() = (required - available).coerceAtLeast(0)
}

sealed class StockCheckResult {
    object Ok : StockCheckResult()
    data class Insufficient(val items: List<InsufficientStockInfo>) : StockCheckResult()
}

class RepairRepository(private val db: AppDatabase, private val context: Context) {

    private val repairDao = db.repairDao()
    private val repairItemDao = db.repairItemDao()
    private val paymentDao = db.paymentDao()
    private val statusHistoryDao = db.statusHistoryDao()
    private val customerDao = db.customerDao()
    private val brandDao = db.brandDao()
    private val modelDao = db.modelDao()
    private val faultDao = db.faultDao()
    private val repairTypeDao = db.repairTypeDao()
    private val priceDao = db.priceDao()
    private val universalRepairTypeDao = db.universalRepairTypeDao()
    private val stockItemDao = db.stockItemDao()
    private val stockMovementDao = db.stockMovementDao()
    private val stockRepairConnectionDao = db.stockRepairConnectionDao()
    private val settingsDao = db.settingsDao()
    private val scannerLearningDao = db.scannerLearningDao()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processedReadyTransitions = Collections.synchronizedSet(mutableSetOf<String>())
    private val prefs by lazy {
        context.getSharedPreferences("udm_ready_sms_transitions", Context.MODE_PRIVATE)
    }

    private val _smsEvents = MutableSharedFlow<SmsEvent>(extraBufferCapacity = 64)
    val smsEvents: SharedFlow<SmsEvent> = _smsEvents.asSharedFlow()

    private fun markTransitionProcessed(marker: String): Boolean {
        synchronized(processedReadyTransitions) {
            if (processedReadyTransitions.contains(marker) || prefs.getBoolean(marker, false)) {
                return false
            }
            processedReadyTransitions.add(marker)
            try {
                prefs.edit().putBoolean(marker, true).apply()
            } catch (e: Exception) {
                // Ignore SharedPreferences failure, in-memory set handles this session
            }
            return true
        }
    }

    val allRepairs: Flow<List<RepairEntity>> = repairDao.getAllRepairsFlow()
    val allPayments: Flow<List<PaymentEntity>> = paymentDao.getAllPaymentsFlow()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()
    val allBrands: Flow<List<BrandEntity>> = brandDao.getAllBrandsFlow()
    val allFaults: Flow<List<FaultEntity>> = faultDao.getAllFaultsFlow()
    val allRepairTypes: Flow<List<RepairTypeEntity>> = repairTypeDao.getAllRepairTypesFlow()
    val allPrices: Flow<List<PriceEntity>> = priceDao.getAllPricesFlow()
    val allUniversalRepairTypes: Flow<List<UniversalRepairTypeEntity>> = universalRepairTypeDao.getAllUniversalRepairTypesFlow()
    val allStockItems: Flow<List<StockItemEntity>> = stockItemDao.getAllStockItemsFlow()
    val allStockMovements: Flow<List<StockMovementEntity>> = stockMovementDao.getAllMovementsFlow()
    val allStockConnections: Flow<List<StockRepairConnectionEntity>> = stockRepairConnectionDao.getAllConnectionsFlow()
    val settingsFlow: Flow<AppSettingsEntity?> = settingsDao.getSettingsFlow()
    val scannerLearningRecords: Flow<List<ScannerLearningEntity>> = scannerLearningDao.getAllRecordsFlow()
    val scannerLearningCount: Flow<Int> = scannerLearningDao.getRecordCountFlow()

    fun getModelsForBrand(brand: String): Flow<List<ModelEntity>> = modelDao.getModelsForBrand(brand)

    fun getItemsForRepair(repairId: Long): Flow<List<RepairItemEntity>> = repairItemDao.getItemsForRepair(repairId)
    suspend fun getItemsForRepairDirect(repairId: Long): List<RepairItemEntity> = repairItemDao.getItemsForRepairDirect(repairId)

    fun getPaymentsForRepair(repairId: Long): Flow<List<PaymentEntity>> = paymentDao.getPaymentsForRepair(repairId)
    suspend fun getPaymentsForRepairDirect(repairId: Long): List<PaymentEntity> = paymentDao.getPaymentsForRepairDirect(repairId)

    fun getHistoryForRepair(repairId: Long): Flow<List<StatusHistoryEntity>> = statusHistoryDao.getHistoryForRepair(repairId)

    suspend fun getRepairById(id: Long): RepairEntity? = repairDao.getRepairById(id)

    suspend fun getRepairByJobNumber(jobNumber: String): RepairEntity? = withContext(Dispatchers.IO) {
        val trimmed = jobNumber.trim()
        if (trimmed.isEmpty()) return@withContext null

        // 1. Direct search preserving exact string and leading zeros (e.g. "00125")
        val direct = repairDao.getRepairByJobNumber(trimmed)
        if (direct != null) return@withContext direct

        // 2. Normalized 4-digit search fallback
        val normalized = normalizeJobNumber(trimmed)
        if (normalized.isNotEmpty() && normalized != trimmed) {
            val normMatch = repairDao.getRepairByJobNumber(normalized)
            if (normMatch != null) return@withContext normMatch
        }

        // 3. Fallback: match by numerical value across all existing repairs
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            val all = repairDao.getAllRepairsDirect()
            return@withContext all.firstOrNull {
                it.jobNumber == trimmed ||
                it.jobNumber == digits ||
                it.jobNumber.trimStart('0') == digits.trimStart('0')
            }
        }
        null
    }

    suspend fun getRepairsForCustomer(phone: String): List<RepairEntity> = repairDao.getRepairsByCustomerPhone(phone)

    suspend fun getSettings(): AppSettingsEntity {
        return settingsDao.getSettings() ?: AppSettingsEntity()
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        settingsDao.insertOrUpdate(settings)
    }

    // 4-Digit Job Number Generator
    suspend fun generateNextJobNumber(): String = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val allNumbers = repairDao.getAllJobNumbers()
        var maxNum = settings.startingJobNumber - 1

        for (numStr in allNumbers) {
            val num = numStr.toIntOrNull()
            if (num != null && num > maxNum) {
                maxNum = num
            }
        }
        val next = maxNum + 1
        String.format(Locale.US, "%04d", next)
    }

    fun normalizeJobNumber(raw: String): String {
        val digitsOnly = raw.replace(Regex("[^0-9]"), "")
        if (digitsOnly.isEmpty()) return ""
        val num = digitsOnly.toIntOrNull() ?: 0
        return String.format(Locale.US, "%04d", num)
    }

    // Customer History Lookup by Phone
    suspend fun getCustomerSummaryByPhone(phone: String): Pair<CustomerEntity?, List<RepairEntity>> = withContext(Dispatchers.IO) {
        val clean = phone.filter { it.isDigit() }
        if (clean.length < 3) return@withContext null to emptyList()
        val allRepairs = repairDao.getAllRepairsDirect()
        val matchingRepairs = allRepairs.filter { repair ->
            val repClean = repair.customerPhone.filter { it.isDigit() }
            repClean.isNotEmpty() && (repClean == clean || repClean.endsWith(clean) || clean.endsWith(repClean))
        }
        val allCustomers = customerDao.getAllCustomersDirect()
        val matchingCustomer = allCustomers.find { customer ->
            val custClean = customer.phone.filter { it.isDigit() }
            custClean.isNotEmpty() && (custClean == clean || custClean.endsWith(clean) || clean.endsWith(custClean))
        }
        matchingCustomer to matchingRepairs
    }

    // Update Customer Details & Relationships
    suspend fun updateCustomer(
        oldPhone: String,
        newPhone: String,
        newName: String
    ): UpdateCustomerResult = withContext(Dispatchers.IO) {
        val trimmedOldPhone = oldPhone.trim()
        val trimmedNewPhone = newPhone.trim()
        val trimmedNewName = newName.trim()

        if (trimmedNewName.isBlank()) {
            return@withContext UpdateCustomerResult.ValidationError("Please enter customer name")
        }
        if (trimmedNewPhone.isBlank()) {
            return@withContext UpdateCustomerResult.ValidationError("Please enter customer phone number")
        }
        val digits = trimmedNewPhone.filter { it.isDigit() }
        if (digits.length < 3) {
            return@withContext UpdateCustomerResult.ValidationError("Phone number must contain at least 3 digits")
        }

        val allCustomers = customerDao.getAllCustomersDirect()
        val oldCleanDigits = trimmedOldPhone.filter { it.isDigit() }

        if (!trimmedNewPhone.equals(trimmedOldPhone, ignoreCase = true)) {
            // Check if newPhone already belongs to another customer (by exact phone or exact digit match)
            val conflictCustomer = allCustomers.find { c ->
                val cClean = c.phone.filter { it.isDigit() }
                (c.phone.equals(trimmedNewPhone, ignoreCase = true) || (cClean.isNotEmpty() && cClean == digits)) &&
                    !c.phone.equals(trimmedOldPhone, ignoreCase = true) &&
                    cClean != oldCleanDigits
            }

            if (conflictCustomer != null) {
                return@withContext UpdateCustomerResult.PhoneConflict(conflictCustomer.name)
            }

            // Also check if any existing repairs belong to another customer with this phone
            val repairsWithNewPhone = repairDao.getRepairsByCustomerPhone(trimmedNewPhone)
            val conflictRepairs = repairsWithNewPhone.filter {
                val repClean = it.customerPhone.filter { d -> d.isDigit() }
                !it.customerPhone.equals(trimmedOldPhone, ignoreCase = true) && repClean != oldCleanDigits
            }
            if (conflictRepairs.isNotEmpty()) {
                val conflictingName = conflictRepairs.firstOrNull()?.customerName ?: "another customer"
                return@withContext UpdateCustomerResult.PhoneConflict(conflictingName)
            }

            // Get old customer data to preserve timestamp
            val oldCust = customerDao.getCustomerByPhone(trimmedOldPhone)
            val lastVisit = oldCust?.lastVisitTimestamp ?: System.currentTimeMillis()

            // Safe update: delete old customer key and insert updated record
            customerDao.deleteCustomerByPhone(trimmedOldPhone)
            for (c in allCustomers) {
                val cClean = c.phone.filter { it.isDigit() }
                if (cClean.isNotEmpty() && cClean == oldCleanDigits) {
                    customerDao.deleteCustomerByPhone(c.phone)
                }
            }
            customerDao.insertCustomer(
                CustomerEntity(
                    phone = trimmedNewPhone,
                    name = trimmedNewName,
                    lastVisitTimestamp = lastVisit
                )
            )

            // Update customer relationship across all existing repairs
            repairDao.updateCustomerDetailsForRepairs(trimmedOldPhone, trimmedNewPhone, trimmedNewName)
            val allRepairs = repairDao.getAllRepairsDirect()
            for (rep in allRepairs) {
                val repClean = rep.customerPhone.filter { it.isDigit() }
                if (rep.customerPhone == trimmedOldPhone || (oldCleanDigits.isNotEmpty() && repClean == oldCleanDigits)) {
                    repairDao.updateRepair(rep.copy(customerPhone = trimmedNewPhone, customerName = trimmedNewName))
                }
            }
        } else {
            // Phone is unchanged, only name is updated
            val oldCust = customerDao.getCustomerByPhone(trimmedOldPhone)
            val lastVisit = oldCust?.lastVisitTimestamp ?: System.currentTimeMillis()

            customerDao.insertCustomer(
                CustomerEntity(
                    phone = trimmedOldPhone,
                    name = trimmedNewName,
                    lastVisitTimestamp = lastVisit
                )
            )

            // Update customer name across all existing repairs for this phone
            repairDao.updateCustomerNameForRepairs(trimmedOldPhone, trimmedNewName)
            val allRepairs = repairDao.getAllRepairsDirect()
            for (rep in allRepairs) {
                val repClean = rep.customerPhone.filter { it.isDigit() }
                if (rep.customerPhone == trimmedOldPhone || (oldCleanDigits.isNotEmpty() && repClean == oldCleanDigits)) {
                    repairDao.updateRepair(rep.copy(customerName = trimmedNewName))
                }
            }
        }

        UpdateCustomerResult.Success
    }

    // Safely delete customer entry from directory while leaving historical repairs, payments, and balances intact
    suspend fun deleteCustomer(phone: String) = withContext(Dispatchers.IO) {
        val trimmedPhone = phone.trim()
        val cleanDigits = trimmedPhone.filter { it.isDigit() }
        customerDao.deleteCustomerByPhone(trimmedPhone)
        if (cleanDigits.length >= 3) {
            val allCustomers = customerDao.getAllCustomersDirect()
            for (c in allCustomers) {
                val cClean = c.phone.filter { it.isDigit() }
                if (cClean.isNotEmpty() && cClean == cleanDigits) {
                    customerDao.deleteCustomerByPhone(c.phone)
                }
            }
        }
    }

    // Price Lookup with Priority:
    // 1. Exact Brand + Model + Repair Type
    // 2. Universal Repair Type (fixed default price across all models)
    // 3. Null (missing price)
    suspend fun lookupPrice(brand: String, model: String, repairType: String): Double? = withContext(Dispatchers.IO) {
        val b = brand.trim()
        val m = model.trim()
        val r = repairType.trim()
        if (r.isEmpty()) return@withContext null

        // Priority 1: Exact Brand + Model + Repair Type
        if (b.isNotEmpty() && m.isNotEmpty()) {
            val entity = priceDao.findPrice(b, m, r)
            if (entity != null) {
                return@withContext entity.price
            }
        }

        // Priority 2: Universal Repair Type
        val universal = universalRepairTypeDao.findByName(r)
        if (universal != null) {
            return@withContext universal.defaultPrice
        }

        // Priority 3: Neither exists
        null
    }

    suspend fun savePrice(brand: String, model: String, repairType: String, price: Double) = withContext(Dispatchers.IO) {
        val existing = priceDao.findPrice(brand.trim(), model.trim(), repairType.trim())
        priceDao.insertPrice(
            PriceEntity(
                id = existing?.id ?: 0L,
                brand = brand.trim(),
                model = model.trim(),
                repairType = repairType.trim(),
                price = price
            )
        )
    }

    suspend fun deletePrice(price: PriceEntity) = withContext(Dispatchers.IO) {
        priceDao.deletePrice(price)
    }

    // Universal Repair Types
    suspend fun saveUniversalRepairType(name: String, defaultPrice: Double) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext
        val existing = universalRepairTypeDao.findByName(trimmed)
        universalRepairTypeDao.insertUniversalRepairType(
            UniversalRepairTypeEntity(
                id = existing?.id ?: 0L,
                name = trimmed,
                defaultPrice = defaultPrice
            )
        )
        addRepairTypeIfNew(trimmed)
    }

    suspend fun updateUniversalRepairType(entity: UniversalRepairTypeEntity) = withContext(Dispatchers.IO) {
        val trimmed = entity.name.trim()
        if (trimmed.isEmpty()) return@withContext
        val existingWithSameName = universalRepairTypeDao.findByName(trimmed)
        val targetId = if (existingWithSameName != null && existingWithSameName.id != entity.id) {
            existingWithSameName.id
        } else {
            entity.id
        }
        universalRepairTypeDao.insertUniversalRepairType(
            entity.copy(id = targetId, name = trimmed)
        )
        addRepairTypeIfNew(trimmed)
    }

    suspend fun deleteUniversalRepairType(entity: UniversalRepairTypeEntity) = withContext(Dispatchers.IO) {
        universalRepairTypeDao.deleteUniversalRepairType(entity)
    }

    suspend fun deleteUniversalRepairTypeById(id: Long) = withContext(Dispatchers.IO) {
        universalRepairTypeDao.deleteUniversalRepairTypeById(id)
    }

    // --- Stock Management Methods ---
    suspend fun getAllStockItemsDirect(): List<StockItemEntity> = withContext(Dispatchers.IO) {
        stockItemDao.getAllStockItemsDirect()
    }

    suspend fun getStockItemById(id: Long): StockItemEntity? = withContext(Dispatchers.IO) {
        stockItemDao.getStockItemById(id)
    }

    fun getStockItemByIdFlow(id: Long): Flow<StockItemEntity?> = stockItemDao.getStockItemByIdFlow(id)

    fun getMovementsForStockItem(stockItemId: Long): Flow<List<StockMovementEntity>> =
        stockMovementDao.getMovementsForStockItem(stockItemId)

    suspend fun getMovementsForStockItemDirect(stockItemId: Long): List<StockMovementEntity> = withContext(Dispatchers.IO) {
        stockMovementDao.getMovementsForStockItemDirect(stockItemId)
    }

    fun getConnectionsForStockItem(stockItemId: Long): Flow<List<StockRepairConnectionEntity>> =
        stockRepairConnectionDao.getConnectionsForStockItem(stockItemId)

    suspend fun getConnectionsForStockItemDirect(stockItemId: Long): List<StockRepairConnectionEntity> = withContext(Dispatchers.IO) {
        stockRepairConnectionDao.getConnectionsForStockItemDirect(stockItemId)
    }

    suspend fun createStockItem(
        name: String,
        quantity: Int,
        purchasePrice: Double = 0.0,
        sellingPrice: Double = 0.0,
        supplier: String = "",
        notes: String = "",
        lowStockWarning: Int = 2
    ): StockItemEntity = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        val existing = stockItemDao.findByName(trimmedName)
        if (existing != null) {
            return@withContext existing
        }
        val now = System.currentTimeMillis()
        val item = StockItemEntity(
            name = trimmedName,
            quantity = quantity.coerceAtLeast(0),
            purchasePrice = purchasePrice.coerceAtLeast(0.0),
            sellingPrice = sellingPrice.coerceAtLeast(0.0),
            supplier = supplier.trim(),
            notes = notes.trim(),
            lowStockWarning = lowStockWarning.coerceAtLeast(0),
            createdTimestamp = now,
            updatedTimestamp = now
        )
        val id = stockItemDao.insertStockItem(item)
        val created = item.copy(id = id)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        stockMovementDao.insertMovement(
            StockMovementEntity(
                stockItemId = id,
                stockItemName = created.name,
                quantityChange = created.quantity,
                resultingQuantity = created.quantity,
                date = dateFormat.format(Date(now)),
                time = timeFormat.format(Date(now)),
                timestamp = now,
                reason = "Initial Stock"
            )
        )
        created
    }

    suspend fun updateStockItem(item: StockItemEntity) = withContext(Dispatchers.IO) {
        stockItemDao.updateStockItem(item.copy(updatedTimestamp = System.currentTimeMillis()))
    }

    suspend fun deleteStockItem(item: StockItemEntity) = withContext(Dispatchers.IO) {
        stockRepairConnectionDao.deleteConnectionsForStockItem(item.id)
        stockMovementDao.deleteMovementsForStockItem(item.id)
        stockItemDao.deleteStockItem(item)
    }

    suspend fun addStock(stockItemId: Long, quantityToAdd: Int, reason: String): StockItemEntity? = withContext(Dispatchers.IO) {
        if (quantityToAdd <= 0) return@withContext null
        val stock = stockItemDao.getStockItemById(stockItemId) ?: return@withContext null
        val now = System.currentTimeMillis()
        val newQuantity = stock.quantity + quantityToAdd
        stockItemDao.updateQuantity(stockItemId, newQuantity, now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        stockMovementDao.insertMovement(
            StockMovementEntity(
                stockItemId = stockItemId,
                stockItemName = stock.name,
                quantityChange = quantityToAdd,
                resultingQuantity = newQuantity,
                date = dateFormat.format(Date(now)),
                time = timeFormat.format(Date(now)),
                timestamp = now,
                reason = reason.trim().ifEmpty { "Added stock (+${quantityToAdd})" }
            )
        )
        stock.copy(quantity = newQuantity, updatedTimestamp = now)
    }

    suspend fun removeStock(stockItemId: Long, quantityToRemove: Int, reason: String): StockItemEntity? = withContext(Dispatchers.IO) {
        if (quantityToRemove <= 0) return@withContext null
        val stock = stockItemDao.getStockItemById(stockItemId) ?: return@withContext null
        val now = System.currentTimeMillis()
        val newQuantity = (stock.quantity - quantityToRemove).coerceAtLeast(0)
        val actualRemoved = stock.quantity - newQuantity
        if (actualRemoved <= 0) return@withContext null
        stockItemDao.updateQuantity(stockItemId, newQuantity, now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        stockMovementDao.insertMovement(
            StockMovementEntity(
                stockItemId = stockItemId,
                stockItemName = stock.name,
                quantityChange = -actualRemoved,
                resultingQuantity = newQuantity,
                date = dateFormat.format(Date(now)),
                time = timeFormat.format(Date(now)),
                timestamp = now,
                reason = reason.trim().ifEmpty { "Manual removal (-${actualRemoved})" }
            )
        )
        stock.copy(quantity = newQuantity, updatedTimestamp = now)
    }

    suspend fun correctStockQuantity(stockItemId: Long, targetQuantity: Int, reason: String): StockItemEntity? = withContext(Dispatchers.IO) {
        val stock = stockItemDao.getStockItemById(stockItemId) ?: return@withContext null
        val correctedQuantity = targetQuantity.coerceAtLeast(0)
        val diff = correctedQuantity - stock.quantity
        if (diff == 0) return@withContext stock
        val now = System.currentTimeMillis()
        stockItemDao.updateQuantity(stockItemId, correctedQuantity, now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        stockMovementDao.insertMovement(
            StockMovementEntity(
                stockItemId = stockItemId,
                stockItemName = stock.name,
                quantityChange = diff,
                resultingQuantity = correctedQuantity,
                date = dateFormat.format(Date(now)),
                time = timeFormat.format(Date(now)),
                timestamp = now,
                reason = reason.trim().ifEmpty { "Quantity correction (${if (diff > 0) "+$diff" else "$diff"})" }
            )
        )
        stock.copy(quantity = correctedQuantity, updatedTimestamp = now)
    }

    suspend fun saveStockConnection(
        stockItemId: Long,
        brand: String,
        model: String,
        repairType: String,
        quantityUsed: Int = 1
    ): StockRepairConnectionEntity = withContext(Dispatchers.IO) {
        val b = brand.trim()
        val m = model.trim()
        val rt = repairType.trim()
        val qty = quantityUsed.coerceAtLeast(1)
        val existing = stockRepairConnectionDao.findExactConnection(b, m, rt)
        if (existing != null) {
            val updated = existing.copy(stockItemId = stockItemId, quantityUsed = qty)
            stockRepairConnectionDao.updateConnection(updated)
            updated
        } else {
            val conn = StockRepairConnectionEntity(
                stockItemId = stockItemId,
                brand = b,
                model = m,
                repairType = rt,
                quantityUsed = qty
            )
            val id = stockRepairConnectionDao.insertConnection(conn)
            conn.copy(id = id)
        }
    }

    suspend fun deleteStockConnection(id: Long) = withContext(Dispatchers.IO) {
        stockRepairConnectionDao.deleteConnectionById(id)
    }

    suspend fun deleteStockConnectionForRepair(brand: String, model: String, repairType: String) = withContext(Dispatchers.IO) {
        stockRepairConnectionDao.deleteConnectionByCombination(brand.trim(), model.trim(), repairType.trim())
    }

    suspend fun findStockConnection(brand: String, model: String, repairType: String): StockRepairConnectionEntity? = withContext(Dispatchers.IO) {
        val b = brand.trim()
        val m = model.trim()
        val rt = repairType.trim()
        // 1. Exact brand + model + repairType match
        val exact = stockRepairConnectionDao.findExactConnection(b, m, rt)
        if (exact != null) return@withContext exact
        // 2. Universal connection fallback
        stockRepairConnectionDao.findUniversalConnection(rt)
    }

    suspend fun checkStockForRepair(repairId: Long): StockCheckResult = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext StockCheckResult.Ok
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val insufficient = mutableListOf<InsufficientStockInfo>()

        val requiredPerStockItem = mutableMapOf<Long, Pair<StockItemEntity, Int>>()
        for (item in items) {
            if (!item.stockDeducted) {
                val conn = findStockConnection(repair.brand, repair.model, item.repairType)
                if (conn != null) {
                    val stock = stockItemDao.getStockItemById(conn.stockItemId)
                    if (stock != null) {
                        val currentTotal = requiredPerStockItem[stock.id]?.second ?: 0
                        requiredPerStockItem[stock.id] = stock to (currentTotal + conn.quantityUsed)
                    }
                }
            }
        }

        for ((stockId, pair) in requiredPerStockItem) {
            val (stock, reqQty) = pair
            if (stock.quantity < reqQty) {
                insufficient.add(
                    InsufficientStockInfo(
                        stockItem = stock,
                        available = stock.quantity,
                        required = reqQty,
                        repairItemType = stock.name
                    )
                )
            }
        }

        if (insufficient.isNotEmpty()) {
            StockCheckResult.Insufficient(insufficient)
        } else {
            StockCheckResult.Ok
        }
    }

    // Autocomplete Master Data
    suspend fun addBrandIfNew(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = brandDao.getAllBrandsDirect()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            brandDao.insertBrand(BrandEntity(name = trimmed))
            true
        } else false
    }

    suspend fun addModelIfNew(brand: String, modelName: String): Boolean = withContext(Dispatchers.IO) {
        val bTrimmed = brand.trim()
        val mTrimmed = modelName.trim()
        if (bTrimmed.isEmpty() || mTrimmed.isEmpty()) return@withContext false
        val existing = modelDao.getModelsForBrandDirect(bTrimmed)
        if (existing.none { it.modelName.equals(mTrimmed, ignoreCase = true) }) {
            modelDao.insertModel(ModelEntity(brandName = bTrimmed, modelName = mTrimmed))
            true
        } else false
    }

    suspend fun addFaultIfNew(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = faultDao.getAllFaultsDirect()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            faultDao.insertFault(FaultEntity(name = trimmed))
            true
        } else false
    }

    suspend fun addRepairTypeIfNew(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = repairTypeDao.getAllRepairTypesDirect()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            repairTypeDao.insertRepairType(RepairTypeEntity(name = trimmed))
            true
        } else false
    }

    // Creating a New Repair
    suspend fun createRepair(
        customerName: String,
        customerPhone: String,
        brand: String,
        model: String,
        imei: String = "",
        deviceColour: String = "",
        accessories: String = "",
        fault: String = "",
        technicianNotes: String = "",
        remarks: String = "",
        repairItems: List<Pair<String, Double>> = emptyList(),
        initialPaymentAmount: Double = 0.0
    ): RepairEntity = withContext(Dispatchers.IO) {
        val jobNumber = generateNextJobNumber()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(now))
        val timeStr = timeFormat.format(Date(now))

        val totalPrice = repairItems.sumOf { it.second }
        val amountPaid = initialPaymentAmount
        val balance = (totalPrice - amountPaid).coerceAtLeast(0.0)

        val paymentStatus = when {
            totalPrice > 0 && amountPaid >= totalPrice -> "PAID"
            amountPaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }

        // New repairs start as RECEIVED (pending completion), preserving customer details and repair items without deducting stock
        val initialStatus = "RECEIVED"
        val deliveredTimestamp: Long? = null

        val repairEntity = RepairEntity(
            jobNumber = jobNumber,
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            brand = brand.trim(),
            model = model.trim(),
            imei = imei.trim(),
            deviceColour = deviceColour.trim(),
            accessories = accessories.trim(),
            fault = fault.trim(),
            technicianNotes = technicianNotes.trim(),
            remarks = remarks.trim(),
            totalPrice = totalPrice,
            amountPaid = amountPaid,
            balance = balance,
            paymentStatus = paymentStatus,
            status = initialStatus,
            receivedDate = dateStr,
            receivedTime = timeStr,
            receivedTimestamp = now,
            deliveredTimestamp = deliveredTimestamp,
            readySmsSent = false
        )

        val repairId = repairDao.insertRepair(repairEntity)
        val createdRepair = repairEntity.copy(id = repairId)

        // Insert repair items
        val itemEntities = repairItems.map {
            RepairItemEntity(repairId = repairId, repairType = it.first.trim(), price = it.second)
        }
        repairItemDao.insertItems(itemEntities)

        // Insert initial payment if any
        if (initialPaymentAmount > 0) {
            paymentDao.insertPayment(
                PaymentEntity(
                    repairId = repairId,
                    paymentNumber = 1,
                    amount = initialPaymentAmount,
                    date = dateStr,
                    time = timeStr,
                    timestamp = now
                )
            )
        }

        // Insert status history
        statusHistoryDao.insertHistory(
            StatusHistoryEntity(
                repairId = repairId,
                status = initialStatus,
                date = dateStr,
                time = timeStr,
                timestamp = now,
                notes = "Job registered"
            )
        )

        // Save / update customer
        customerDao.insertCustomer(
            CustomerEntity(
                phone = customerPhone.trim(),
                name = customerName.trim(),
                lastVisitTimestamp = now
            )
        )

        // Save any custom brand/model/fault/repairType into master data
        addBrandIfNew(brand)
        addModelIfNew(brand, model)
        addFaultIfNew(fault)
        repairItems.forEach { addRepairTypeIfNew(it.first) }

        // Trigger automatic customer registration SMS (phone received) AFTER repair is successfully saved
        val currentSettings = getSettings()
        if (currentSettings.autoRegistrationSms && createdRepair.customerPhone.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                performAutoRegistrationSms(createdRepair)
            }
        }

        createdRepair
    }

    // Payment Operations
    suspend fun addPayment(repairId: Long, amount: Double, emergencyAdjustmentReason: String? = null): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext null
        if (amount <= 0) return@withContext repair

        val existingPayments = paymentDao.getPaymentsForRepairDirect(repairId)
        val nextNumber = existingPayments.size + 1
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(now))
        val timeStr = timeFormat.format(Date(now))

        paymentDao.insertPayment(
            PaymentEntity(
                repairId = repairId,
                paymentNumber = nextNumber,
                amount = amount,
                date = dateStr,
                time = timeStr,
                timestamp = now
            )
        )

        recalculateAndSaveRepair(repairId, emergencyAdjustmentReason)
    }

    suspend fun updatePayment(paymentId: Long, newAmount: Double): RepairEntity? = withContext(Dispatchers.IO) {
        val allPayments = paymentDao.getAllPaymentsDirect()
        val payment = allPayments.find { it.id == paymentId } ?: return@withContext null
        if (newAmount <= 0) return@withContext null

        paymentDao.updatePayment(payment.copy(amount = newAmount))
        recalculateAndSaveRepair(payment.repairId)
    }

    suspend fun deletePayment(paymentId: Long): RepairEntity? = withContext(Dispatchers.IO) {
        val allPayments = paymentDao.getAllPaymentsDirect()
        val payment = allPayments.find { it.id == paymentId } ?: return@withContext null
        val repairId = payment.repairId

        paymentDao.deletePaymentById(paymentId)
        recalculateAndSaveRepair(repairId)
    }

    // Add / Remove Repair Items
    suspend fun addRepairItem(repairId: Long, repairType: String, price: Double): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext null
        val isCompleted = (repair.status == "READY" || repair.status == "DELIVERED")
        var deducted = false
        var sItemId: Long? = null
        var dQty = 0

        val conn = findStockConnection(repair.brand, repair.model, repairType)
        if (isCompleted && conn != null) {
            val stock = stockItemDao.getStockItemById(conn.stockItemId)
            if (stock != null && stock.quantity >= conn.quantityUsed) {
                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val newQty = (stock.quantity - conn.quantityUsed).coerceAtLeast(0)
                stockItemDao.updateQuantity(stock.id, newQty, now)
                val itemId = repairItemDao.insertItem(
                    RepairItemEntity(
                        repairId = repairId,
                        repairType = repairType.trim(),
                        price = price,
                        stockDeducted = true,
                        stockItemId = stock.id,
                        stockDeductedQty = conn.quantityUsed
                    )
                )
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        stockItemId = stock.id,
                        stockItemName = stock.name,
                        quantityChange = -conn.quantityUsed,
                        resultingQuantity = newQty,
                        date = dateFormat.format(Date(now)),
                        time = timeFormat.format(Date(now)),
                        timestamp = now,
                        reason = "Used in Job #${repair.jobNumber} (${repair.brand} ${repair.model} - ${repairType.trim()})",
                        repairId = repair.id,
                        repairItemId = itemId
                    )
                )
                addRepairTypeIfNew(repairType)
                return@withContext recalculateAndSaveRepair(repairId)
            }
        }

        repairItemDao.insertItem(
            RepairItemEntity(
                repairId = repairId,
                repairType = repairType.trim(),
                price = price,
                stockDeducted = deducted,
                stockItemId = sItemId,
                stockDeductedQty = dQty
            )
        )
        addRepairTypeIfNew(repairType)
        recalculateAndSaveRepair(repairId)
    }

    suspend fun removeRepairItem(itemId: Long, repairId: Long): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId)
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val item = items.find { it.id == itemId } ?: return@withContext null
        if (item.stockDeducted && item.stockItemId != null && item.stockDeductedQty > 0) {
            val stock = stockItemDao.getStockItemById(item.stockItemId)
            if (stock != null) {
                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val newQty = stock.quantity + item.stockDeductedQty
                stockItemDao.updateQuantity(stock.id, newQty, now)
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        stockItemId = stock.id,
                        stockItemName = stock.name,
                        quantityChange = item.stockDeductedQty,
                        resultingQuantity = newQty,
                        date = dateFormat.format(Date(now)),
                        time = timeFormat.format(Date(now)),
                        timestamp = now,
                        reason = "Restored: Removed from Job #${repair?.jobNumber ?: repairId}",
                        repairId = repairId,
                        repairItemId = item.id
                    )
                )
            }
        }
        repairItemDao.deleteItem(item)
        recalculateAndSaveRepair(repairId)
    }

    suspend fun updateRepairItem(itemId: Long, repairId: Long, newRepairType: String, newPrice: Double): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext null
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val item = items.find { it.id == itemId } ?: return@withContext null
        val isCompleted = (repair.status == "READY" || repair.status == "DELIVERED")

        var newStockDeducted = item.stockDeducted
        var newStockItemId = item.stockItemId
        var newStockDeductedQty = item.stockDeductedQty

        if (isCompleted && item.stockDeducted) {
            val newConn = findStockConnection(repair.brand, repair.model, newRepairType)
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            if (newConn != null && newConn.stockItemId == item.stockItemId && newConn.quantityUsed == item.stockDeductedQty) {
                // Same stock item and quantity, keep as is
            } else {
                // Restore old stock
                if (item.stockItemId != null && item.stockDeductedQty > 0) {
                    val oldStock = stockItemDao.getStockItemById(item.stockItemId)
                    if (oldStock != null) {
                        val restoredQty = oldStock.quantity + item.stockDeductedQty
                        stockItemDao.updateQuantity(oldStock.id, restoredQty, now)
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                stockItemId = oldStock.id,
                                stockItemName = oldStock.name,
                                quantityChange = item.stockDeductedQty,
                                resultingQuantity = restoredQty,
                                date = dateFormat.format(Date(now)),
                                time = timeFormat.format(Date(now)),
                                timestamp = now,
                                reason = "Restored: Item updated in Job #${repair.jobNumber}",
                                repairId = repair.id,
                                repairItemId = item.id
                            )
                        )
                    }
                }
                // Deduct new stock if available
                if (newConn != null) {
                    val newStock = stockItemDao.getStockItemById(newConn.stockItemId)
                    if (newStock != null && newStock.quantity >= newConn.quantityUsed) {
                        val deductedQty = (newStock.quantity - newConn.quantityUsed).coerceAtLeast(0)
                        stockItemDao.updateQuantity(newStock.id, deductedQty, now)
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                stockItemId = newStock.id,
                                stockItemName = newStock.name,
                                quantityChange = -newConn.quantityUsed,
                                resultingQuantity = deductedQty,
                                date = dateFormat.format(Date(now)),
                                time = timeFormat.format(Date(now)),
                                timestamp = now,
                                reason = "Used in Job #${repair.jobNumber} (${repair.brand} ${repair.model} - ${newRepairType.trim()})",
                                repairId = repair.id,
                                repairItemId = item.id
                            )
                        )
                        newStockDeducted = true
                        newStockItemId = newStock.id
                        newStockDeductedQty = newConn.quantityUsed
                    } else {
                        newStockDeducted = false
                        newStockItemId = null
                        newStockDeductedQty = 0
                    }
                } else {
                    newStockDeducted = false
                    newStockItemId = null
                    newStockDeductedQty = 0
                }
            }
        }

        repairItemDao.updateItem(
            item.copy(
                repairType = newRepairType.trim(),
                price = newPrice,
                stockDeducted = newStockDeducted,
                stockItemId = newStockItemId,
                stockDeductedQty = newStockDeductedQty
            )
        )
        addRepairTypeIfNew(newRepairType)
        recalculateAndSaveRepair(repairId)
    }

    // Recalculates payments, balance, payment status, and automatic delivery status
    private suspend fun recalculateAndSaveRepair(repairId: Long, emergencyAdjustmentReason: String? = null): RepairEntity? {
        val repair = repairDao.getRepairById(repairId) ?: return null
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val payments = paymentDao.getPaymentsForRepairDirect(repairId)
        val totalPaid = payments.sumOf { it.amount }

        if (repair.status == "CANCELLED") {
            // Cancelled jobs must strictly retain repair total at Rs. 0 and balance at Rs. 0,
            // while preserving historical payment records accurately.
            val updated = repair.copy(
                totalPrice = 0.0,
                amountPaid = totalPaid,
                balance = 0.0
            )
            repairDao.updateRepair(updated)
            return updated
        }

        val totalPrice = items.sumOf { it.price }
        val balance = (totalPrice - totalPaid).coerceAtLeast(0.0)

        val paymentStatus = when {
            totalPrice > 0 && totalPaid >= totalPrice -> "PAID"
            totalPaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }

        var newStatus = repair.status
        var deliveredTimestamp = repair.deliveredTimestamp
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Automation Rule: If Total Paid >= Total Repair Price -> DELIVERED (only if stock is sufficient or already completed)
        if (totalPrice > 0 && totalPaid >= totalPrice) {
            if (repair.status != "DELIVERED") {
                val wasCompleted = (repair.status == "READY")
                val stockCheck = checkStockForRepair(repair.id)
                if (!wasCompleted && stockCheck is StockCheckResult.Insufficient && emergencyAdjustmentReason.isNullOrBlank()) {
                    // Do not auto-deliver when required stock is unavailable; repair remains pending
                } else {
                    newStatus = "DELIVERED"
                    deliveredTimestamp = now
                    if (!wasCompleted) {
                        deductStockForRepairItems(repair, now, dateFormat.format(Date(now)), timeFormat.format(Date(now)), emergencyAdjustmentReason)
                    }
                    statusHistoryDao.insertHistory(
                        StatusHistoryEntity(
                            repairId = repairId,
                            status = "DELIVERED",
                            date = dateFormat.format(Date(now)),
                            time = timeFormat.format(Date(now)),
                            timestamp = now,
                            notes = "Auto-marked DELIVERED: Full payment received"
                        )
                    )
                }
            }
        }

        val updated = repair.copy(
            totalPrice = totalPrice,
            amountPaid = totalPaid,
            balance = balance,
            paymentStatus = paymentStatus,
            status = newStatus,
            deliveredTimestamp = deliveredTimestamp
        )

        repairDao.updateRepair(updated)
        return updated
    }

    // Status Changes & Automatic SMS
    suspend fun updateStatus(
        repairId: Long,
        newStatus: String,
        notes: String = "",
        emergencyAdjustmentReason: String? = null
    ): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext null
        if (repair.status == newStatus) return@withContext repair

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(now))
        val timeStr = timeFormat.format(Date(now))

        val isTransitionToReady = (newStatus == "READY")
        val isTransitionToCancelled = (newStatus == "CANCELLED")
        val isCompletedStatus = (newStatus == "READY" || newStatus == "DELIVERED")
        val wasCompletedStatus = (repair.status == "READY" || repair.status == "DELIVERED")
        val deliveredTimestamp = if (newStatus == "DELIVERED") now else repair.deliveredTimestamp

        // Stock Deduction & Restoration Rules
        if (isCompletedStatus && !wasCompletedStatus) {
            val stockCheck = checkStockForRepair(repair.id)
            if (stockCheck is StockCheckResult.Insufficient && emergencyAdjustmentReason.isNullOrBlank()) {
                // Prevent completion action until sufficient stock is available or controlled adjustment recorded
                return@withContext repair
            }
            deductStockForRepairItems(repair, now, dateStr, timeStr, emergencyAdjustmentReason)
        } else if (wasCompletedStatus && !isCompletedStatus) {
            restoreStockForRepairItems(repair, newStatus, now, dateStr, timeStr)
        }

        // When transitioning to CANCELLED:
        // Set repair total to Rs. 0 and balance to Rs. 0.
        // Preserve historical payment records and collected amount.
        val updated = if (isTransitionToCancelled) {
            repair.copy(
                status = "CANCELLED",
                totalPrice = 0.0,
                balance = 0.0,
                readySmsSent = false
            )
        } else {
            repair.copy(
                status = newStatus,
                deliveredTimestamp = deliveredTimestamp,
                readySmsSent = if (isTransitionToReady) true else false
            )
        }

        // 1. SAVE STATUS IMMEDIATELY to local database
        repairDao.updateRepair(updated)
        val historyId = statusHistoryDao.insertHistory(
            StatusHistoryEntity(
                repairId = repairId,
                status = newStatus,
                date = dateStr,
                time = timeStr,
                timestamp = now,
                notes = if (isTransitionToCancelled) {
                    notes.trim().ifEmpty { "Job cancelled" }
                } else {
                    notes.ifEmpty { "Status changed to $newStatus" }
                }
            )
        )

        // 2. Immediately initiate Ready SMS asynchronously if transitioning to READY
        if (isTransitionToReady) {
            val transitionMarker = "${repairId}_${historyId}"
            if (markTransitionProcessed(transitionMarker)) {
                repositoryScope.launch(Dispatchers.IO) {
                    performAutoReadySms(updated, transitionMarker)
                }
            }
        }

        // 3. Immediately initiate Cancellation SMS asynchronously if transitioning to CANCELLED
        if (isTransitionToCancelled) {
            val cancelMarker = "cancel_${repairId}_${historyId}"
            if (markTransitionProcessed(cancelMarker)) {
                repositoryScope.launch(Dispatchers.IO) {
                    performAutoCancellationSms(updated, notes.trim(), cancelMarker)
                }
            }
        }

        updated
    }

    private suspend fun deductStockForRepairItems(
        repair: RepairEntity,
        now: Long,
        dateStr: String,
        timeStr: String,
        emergencyAdjustmentReason: String? = null
    ) {
        val items = repairItemDao.getItemsForRepairDirect(repair.id)
        for (item in items) {
            if (!item.stockDeducted) {
                val conn = findStockConnection(repair.brand, repair.model, item.repairType)
                if (conn != null) {
                    val stock = stockItemDao.getStockItemById(conn.stockItemId)
                    if (stock != null) {
                        val reqQty = conn.quantityUsed
                        if (stock.quantity < reqQty && !emergencyAdjustmentReason.isNullOrBlank()) {
                            // Controlled stock adjustment
                            val needed = reqQty - stock.quantity
                            val adjQty = stock.quantity + needed
                            stockItemDao.updateQuantity(stock.id, adjQty, now)
                            stockMovementDao.insertMovement(
                                StockMovementEntity(
                                    stockItemId = stock.id,
                                    stockItemName = stock.name,
                                    quantityChange = needed,
                                    resultingQuantity = adjQty,
                                    date = dateStr,
                                    time = timeStr,
                                    timestamp = now,
                                    reason = "Controlled stock adjustment before Job #${repair.jobNumber}: $emergencyAdjustmentReason"
                                )
                            )
                            val resulting = adjQty - reqQty
                            stockItemDao.updateQuantity(stock.id, resulting, now)
                            stockMovementDao.insertMovement(
                                StockMovementEntity(
                                    stockItemId = stock.id,
                                    stockItemName = stock.name,
                                    quantityChange = -reqQty,
                                    resultingQuantity = resulting,
                                    date = dateStr,
                                    time = timeStr,
                                    timestamp = now,
                                    reason = "Used in Job #${repair.jobNumber} (${repair.brand} ${repair.model} - ${item.repairType})",
                                    repairId = repair.id,
                                    repairItemId = item.id
                                )
                            )
                            repairItemDao.updateItem(
                                item.copy(
                                    stockDeducted = true,
                                    stockItemId = stock.id,
                                    stockDeductedQty = reqQty
                                )
                            )
                        } else if (stock.quantity >= reqQty) {
                            val newQty = (stock.quantity - reqQty).coerceAtLeast(0)
                            stockItemDao.updateQuantity(stock.id, newQty, now)
                            stockMovementDao.insertMovement(
                                StockMovementEntity(
                                    stockItemId = stock.id,
                                    stockItemName = stock.name,
                                    quantityChange = -reqQty,
                                    resultingQuantity = newQty,
                                    date = dateStr,
                                    time = timeStr,
                                    timestamp = now,
                                    reason = "Used in Job #${repair.jobNumber} (${repair.brand} ${repair.model} - ${item.repairType})",
                                    repairId = repair.id,
                                    repairItemId = item.id
                                )
                            )
                            repairItemDao.updateItem(
                                item.copy(
                                    stockDeducted = true,
                                    stockItemId = stock.id,
                                    stockDeductedQty = reqQty
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun restoreStockForRepairItems(repair: RepairEntity, newStatus: String, now: Long, dateStr: String, timeStr: String) {
        val items = repairItemDao.getItemsForRepairDirect(repair.id)
        for (item in items) {
            if (item.stockDeducted && item.stockItemId != null && item.stockDeductedQty > 0) {
                val stock = stockItemDao.getStockItemById(item.stockItemId)
                if (stock != null) {
                    val newQty = stock.quantity + item.stockDeductedQty
                    stockItemDao.updateQuantity(stock.id, newQty, now)
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            stockItemId = stock.id,
                            stockItemName = stock.name,
                            quantityChange = item.stockDeductedQty,
                            resultingQuantity = newQty,
                            date = dateStr,
                            time = timeStr,
                            timestamp = now,
                            reason = "Restored from Job #${repair.jobNumber} ($newStatus)",
                            repairId = repair.id,
                            repairItemId = item.id
                        )
                    )
                }
                repairItemDao.updateItem(
                    item.copy(
                        stockDeducted = false,
                        stockDeductedQty = 0
                    )
                )
            }
        }
    }

    // Cancel Repair Job helper
    suspend fun cancelRepair(repairId: Long, reason: String = ""): RepairEntity? = withContext(Dispatchers.IO) {
        updateStatus(repairId, "CANCELLED", notes = reason.trim())
    }

    private suspend fun performAutoCancellationSms(repair: RepairEntity, reason: String, transitionMarker: String) {
        val settings = getSettings()
        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            _smsEvents.emit(
                SmsEvent(
                    repairId = repair.id,
                    jobNumber = repair.jobNumber,
                    customerPhone = phone,
                    result = SmsSendResult.Failed("Customer phone number is blank"),
                    isAuto = true,
                    message = "Job #${repair.jobNumber} cancelled. SMS not sent: Customer phone number is blank."
                )
            )
            return
        }

        val result = sendCancellationSmsInternal(repair, reason, settings)
        val displayMessage = when (result) {
            is SmsSendResult.SentOrQueued -> "Cancellation SMS sent to $phone"
            is SmsSendResult.PermissionDenied -> "Job #${repair.jobNumber} cancelled. SMS not sent: SMS permission not granted."
            is SmsSendResult.Failed -> "Job #${repair.jobNumber} cancelled. SMS failed: ${result.error}"
            is SmsSendResult.DisabledInSettings -> "Job #${repair.jobNumber} cancelled."
        }

        _smsEvents.emit(
            SmsEvent(
                repairId = repair.id,
                jobNumber = repair.jobNumber,
                customerPhone = phone,
                result = result,
                isAuto = true,
                message = displayMessage
            )
        )
    }

    // Explicit Cancellation SMS Sending (Manual Trigger / Resend)
    suspend fun sendCancellationSmsExplicit(repair: RepairEntity, reason: String = ""): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        sendCancellationSmsInternal(repair, reason, settings)
    }

    fun sendCancellationSmsInternal(repair: RepairEntity, reason: String, settings: AppSettingsEntity): SmsSendResult {
        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            return SmsSendResult.Failed("Customer phone number is blank")
        }

        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        if (!hasPermission) {
            return SmsSendResult.PermissionDenied("SMS permission not granted. Please allow SMS permission in Android settings.")
        }

        return try {
            val reasonSection = if (reason.isNotBlank()) "\nReason: ${reason.trim()}\n" else "\n"
            val text = """
UDM MOBILE REPAIR
Job No: ${repair.jobNumber}

Your repair job has been cancelled.$reasonSection
If you have any questions, please contact us.
${settings.shopName}
${settings.shopPhone}
""".trim().trimIndent()

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            SmsSendResult.SentOrQueued(
                recipient = phone,
                message = "Cancellation SMS sent to $phone"
            )
        } catch (se: SecurityException) {
            SmsSendResult.PermissionDenied(se.localizedMessage ?: "SMS permission denied")
        } catch (e: Exception) {
            SmsSendResult.Failed(e.localizedMessage ?: "Failed to initiate SMS sending")
        }
    }

    private suspend fun performAutoReadySms(repair: RepairEntity, transitionMarker: String) {
        val settings = getSettings()
        if (!settings.autoReadySms) {
            return
        }

        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            _smsEvents.emit(
                SmsEvent(
                    repairId = repair.id,
                    jobNumber = repair.jobNumber,
                    customerPhone = phone,
                    result = SmsSendResult.Failed("Customer phone number is blank"),
                    isAuto = true,
                    message = "Status marked READY. SMS not sent: Customer phone number is blank."
                )
            )
            return
        }

        val result = sendSmsInternal(repair, settings)
        val displayMessage = when (result) {
            is SmsSendResult.SentOrQueued -> "Ready SMS initiated and queued for $phone"
            is SmsSendResult.PermissionDenied -> "Status marked READY. SMS not sent: SMS permission not granted."
            is SmsSendResult.Failed -> "Status marked READY. SMS failed: ${result.error}"
            is SmsSendResult.DisabledInSettings -> "Automatic Ready SMS is disabled in Settings."
        }

        _smsEvents.emit(
            SmsEvent(
                repairId = repair.id,
                jobNumber = repair.jobNumber,
                customerPhone = phone,
                result = result,
                isAuto = true,
                message = displayMessage
            )
        )
    }

    // Send SMS (Manual "SEND SMS AGAIN")
    suspend fun sendReadySmsExplicit(repair: RepairEntity): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        sendSmsInternal(repair, settings)
    }

    private fun sendSmsInternal(repair: RepairEntity, settings: AppSettingsEntity): SmsSendResult {
        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            return SmsSendResult.Failed("Customer phone number is blank")
        }

        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        if (!hasPermission) {
            return SmsSendResult.PermissionDenied("SMS permission not granted. Please allow SMS permission in Android settings.")
        }

        return try {
            val balanceStr = if (repair.balance <= 0) "0" else String.format(Locale.US, "%,.0f", repair.balance)
            val totalStr = String.format(Locale.US, "%,.0f", repair.totalPrice)

            val text = settings.smsTemplate
                .replace("{customer_name}", repair.customerName)
                .replace("{job_number}", repair.jobNumber)
                .replace("{total_price}", totalStr)
                .replace("{balance}", balanceStr)
                .replace("{model}", "${repair.brand} ${repair.model}")
                .replace("{shop_name}", settings.shopName)
                .replace("{shop_phone}", settings.shopPhone)

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            SmsSendResult.SentOrQueued(
                recipient = phone,
                message = "Ready SMS initiated and queued for $phone"
            )
        } catch (se: SecurityException) {
            SmsSendResult.PermissionDenied(se.localizedMessage ?: "SMS permission denied")
        } catch (e: Exception) {
            SmsSendResult.Failed(e.localizedMessage ?: "Failed to initiate SMS sending")
        }
    }

    // Automatic Customer SMS on Job Registration (Phone Received)
    suspend fun performAutoRegistrationSms(repair: RepairEntity): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (!settings.autoRegistrationSms) {
            return@withContext SmsSendResult.DisabledInSettings("Automatic Registration SMS is disabled in Settings.")
        }

        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            val res = SmsSendResult.Failed("Customer phone number is blank")
            _smsEvents.emit(
                SmsEvent(
                    repairId = repair.id,
                    jobNumber = repair.jobNumber,
                    customerPhone = phone,
                    result = res,
                    isAuto = true,
                    message = "Job #${repair.jobNumber} registered. SMS not sent: Customer phone number is blank."
                )
            )
            return@withContext res
        }

        val result = sendRegistrationSmsInternal(repair, settings)
        val displayMessage = when (result) {
            is SmsSendResult.SentOrQueued -> "Registration confirmation SMS sent to $phone"
            is SmsSendResult.PermissionDenied -> "Job #${repair.jobNumber} saved. SMS not sent: SMS permission not granted."
            is SmsSendResult.Failed -> "Job #${repair.jobNumber} saved. SMS failed: ${result.error}"
            is SmsSendResult.DisabledInSettings -> "Automatic Registration SMS is disabled in Settings."
        }

        _smsEvents.emit(
            SmsEvent(
                repairId = repair.id,
                jobNumber = repair.jobNumber,
                customerPhone = phone,
                result = result,
                isAuto = true,
                message = displayMessage
            )
        )
        result
    }

    // Explicit Registration SMS Sending (Manual Trigger / Resend)
    suspend fun sendRegistrationSmsExplicit(repair: RepairEntity): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        sendRegistrationSmsInternal(repair, settings)
    }

    private fun sendRegistrationSmsInternal(repair: RepairEntity, settings: AppSettingsEntity): SmsSendResult {
        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            return SmsSendResult.Failed("Customer phone number is blank")
        }

        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        if (!hasPermission) {
            return SmsSendResult.PermissionDenied("SMS permission not granted. Please allow SMS permission in Android settings.")
        }

        return try {
            val totalStr = if (repair.totalPrice % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", repair.totalPrice)
            } else {
                String.format(Locale.US, "%,.2f", repair.totalPrice)
            }

            val paidStr = if (repair.amountPaid % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", repair.amountPaid)
            } else {
                String.format(Locale.US, "%,.2f", repair.amountPaid)
            }

            val balanceStr = if (repair.balance % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", repair.balance)
            } else {
                String.format(Locale.US, "%,.2f", repair.balance)
            }

            var text = settings.registrationSmsTemplate
            // Automatically replace JOB_NO, TOTAL, PAID and BALANCE with actual job values (case-insensitive)
            text = text.replace("{JOB_NO}", repair.jobNumber, ignoreCase = true)
                .replace("{JOB_NUMBER}", repair.jobNumber, ignoreCase = true)
                .replace("{TOTAL}", totalStr, ignoreCase = true)
                .replace("{TOTAL_PRICE}", totalStr, ignoreCase = true)
                .replace("{PAID}", paidStr, ignoreCase = true)
                .replace("{AMOUNT_PAID}", paidStr, ignoreCase = true)
                .replace("{BALANCE}", balanceStr, ignoreCase = true)
                .replace("{CUSTOMER_NAME}", repair.customerName, ignoreCase = true)
                .replace("{MODEL}", "${repair.brand} ${repair.model}", ignoreCase = true)
                .replace("{SHOP_NAME}", settings.shopName, ignoreCase = true)
                .replace("{SHOP_PHONE}", settings.shopPhone, ignoreCase = true)

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            SmsSendResult.SentOrQueued(
                recipient = phone,
                message = "Registration SMS initiated and queued for $phone"
            )
        } catch (se: SecurityException) {
            SmsSendResult.PermissionDenied(se.localizedMessage ?: "SMS permission denied")
        } catch (e: Exception) {
            SmsSendResult.Failed(e.localizedMessage ?: "Failed to initiate SMS sending")
        }
    }

    // Full Repair Details Updating
    suspend fun updateRepairDetails(
        repairId: Long,
        customerName: String,
        customerPhone: String,
        brand: String,
        model: String,
        imei: String,
        deviceColour: String,
        accessories: String,
        fault: String,
        technicianNotes: String,
        remarks: String
    ) = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext
        val updated = repair.copy(
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            brand = brand.trim(),
            model = model.trim(),
            imei = imei.trim(),
            deviceColour = deviceColour.trim(),
            accessories = accessories.trim(),
            fault = fault.trim(),
            technicianNotes = technicianNotes.trim(),
            remarks = remarks.trim()
        )
        repairDao.updateRepair(updated)
        customerDao.insertCustomer(
            CustomerEntity(
                phone = customerPhone.trim(),
                name = customerName.trim(),
                lastVisitTimestamp = System.currentTimeMillis()
            )
        )
        addBrandIfNew(brand)
        addModelIfNew(brand, model)
        addFaultIfNew(fault)
    }

    suspend fun deleteRepair(repairId: Long) = withContext(Dispatchers.IO) {
        repairItemDao.deleteItemsForRepair(repairId)
        paymentDao.deletePaymentsForRepair(repairId)
        statusHistoryDao.deleteHistoryForRepair(repairId)
        repairDao.deleteRepairById(repairId)
    }

    // Master Data Deletion/Editing
    suspend fun deleteBrand(brand: BrandEntity) = withContext(Dispatchers.IO) { brandDao.deleteBrand(brand) }
    suspend fun deleteModel(model: ModelEntity) = withContext(Dispatchers.IO) { modelDao.deleteModel(model) }
    suspend fun deleteFault(fault: FaultEntity) = withContext(Dispatchers.IO) { faultDao.deleteFault(fault) }
    suspend fun deleteRepairType(type: RepairTypeEntity) = withContext(Dispatchers.IO) { repairTypeDao.deleteRepairType(type) }

    // Scanner Adaptive Learning (Storage-Safe)
    suspend fun saveScannerLearning(
        rawOcrText: String,
        confirmedJobNumber: String,
        detectedJobNumber: String = "",
        confidence: Float = 0f,
        preprocessingMethod: String = "",
        characterSubstitutions: String = ""
    ) = withContext(Dispatchers.IO) {
        val cleanRaw = rawOcrText.trim()
        val cleanConfirmed = confirmedJobNumber.trim()
        if (cleanRaw.isBlank() || cleanConfirmed.isBlank()) return@withContext

        val existing = scannerLearningDao.findRecord(cleanRaw, cleanConfirmed)
        if (existing != null) {
            val updated = existing.copy(
                timesEncountered = existing.timesEncountered + 1,
                timestamp = System.currentTimeMillis(),
                confidence = if (confidence > 0) confidence else existing.confidence,
                preprocessingMethod = preprocessingMethod.ifEmpty { existing.preprocessingMethod },
                characterSubstitutions = characterSubstitutions.ifEmpty { existing.characterSubstitutions }
            )
            scannerLearningDao.updateRecord(updated)
        } else {
            scannerLearningDao.insertRecord(
                ScannerLearningEntity(
                    rawOcrText = cleanRaw,
                    confirmedJobNumber = cleanConfirmed,
                    detectedJobNumber = detectedJobNumber.trim(),
                    confidence = confidence,
                    preprocessingMethod = preprocessingMethod,
                    characterSubstitutions = characterSubstitutions,
                    timestamp = System.currentTimeMillis(),
                    timesEncountered = 1
                )
            )
        }
        // Never fill phone storage: automatically prune oldest/least frequent records beyond 100
        scannerLearningDao.pruneOldRecords(100)
    }

    suspend fun resetScannerLearning() = withContext(Dispatchers.IO) {
        scannerLearningDao.deleteAllRecords()
    }

    suspend fun getAllScannerLearningDirect(): List<ScannerLearningEntity> = withContext(Dispatchers.IO) {
        scannerLearningDao.getAllRecordsDirect()
    }

    // Direct access for backup & restore
    suspend fun getAllDataForBackup() = withContext(Dispatchers.IO) {
        val custTmpl = ReceiptPreferences.getCustomerTemplate(context)
        val payTmpl = ReceiptPreferences.getPaymentTemplate(context)
        val custCfg = ReceiptPreferences.getCustomerReceiptConfig(context)
        val payCfg = ReceiptPreferences.getPaymentReceiptConfig(context)
        val p50s = PrinterPreferences.getP50SSettings(context)
        val autoPrint = PrinterPreferences.isAutoPrintEnabled(context)
        val diagMode = PrinterPreferences.isCustomerReceiptDiagnosticMode(context)

        // Capture all referenced and available logo image files
        val logos = mutableListOf<ReceiptLogoBackup>()
        val seenFileNames = mutableSetOf<String>()

        val logoElements = (custTmpl.elements + payTmpl.elements).filter {
            it.type == ReceiptElementType.IMAGE_LOGO && it.logoPath.isNotBlank()
        }

        for (el in logoElements) {
            val file = File(el.logoPath)
            if (file.exists() && file.canRead() && !seenFileNames.contains(file.name)) {
                seenFileNames.add(file.name)
                val bytes = file.readBytes()
                if (bytes.isNotEmpty()) {
                    val b64 = Base64Helper.encode(bytes)
                    logos.add(ReceiptLogoBackup(fileName = file.name, base64Data = b64))
                }
            }
        }

        // Also check if there are other saved logos in the receipt_logos directory
        try {
            val logoDir = File(context.filesDir, "receipt_logos")
            if (logoDir.exists() && logoDir.isDirectory) {
                val files = logoDir.listFiles() ?: emptyArray()
                for (f in files) {
                    if (f.isFile && f.length() > 0 && !seenFileNames.contains(f.name)) {
                        seenFileNames.add(f.name)
                        val bytes = f.readBytes()
                        if (bytes.isNotEmpty()) {
                            val b64 = Base64Helper.encode(bytes)
                            logos.add(ReceiptLogoBackup(fileName = f.name, base64Data = b64))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val receiptData = ReceiptBackupData(
            customerTemplate = custTmpl,
            paymentTemplate = payTmpl,
            customerConfig = custCfg,
            paymentConfig = payCfg,
            paperWidthDots = 384,
            autoPrintCustomerReceipt = autoPrint,
            customerReceiptDiagnosticMode = diagMode,
            p50sSettings = p50s,
            logos = logos
        )

        BackupData(
            repairs = repairDao.getAllRepairsDirect(),
            repairItems = repairItemDao.getAllRepairItemsDirect(),
            payments = paymentDao.getAllPaymentsDirect(),
            statusHistory = statusHistoryDao.getAllHistoryDirect(),
            customers = customerDao.getAllCustomersDirect(),
            brands = brandDao.getAllBrandsDirect(),
            models = modelDao.getAllModelsDirect(),
            faults = faultDao.getAllFaultsDirect(),
            repairTypes = repairTypeDao.getAllRepairTypesDirect(),
            prices = priceDao.getAllPricesDirect(),
            settings = settingsDao.getSettings() ?: AppSettingsEntity(),
            scannerLearning = scannerLearningDao.getAllRecordsDirect(),
            universalRepairTypes = universalRepairTypeDao.getAllUniversalRepairTypesDirect(),
            stockItems = stockItemDao.getAllStockItemsDirect(),
            stockConnections = stockRepairConnectionDao.getAllConnectionsDirect(),
            stockMovements = stockMovementDao.getAllMovementsDirect(),
            receiptCustomization = receiptData
        )
    }

    suspend fun restoreData(backup: BackupData, clearExisting: Boolean = true) = withContext(Dispatchers.IO) {
        if (clearExisting) {
            db.clearAllTables()
        }
        if (backup.brands.isNotEmpty()) {
            for (b in backup.brands) brandDao.insertBrand(b)
        } else {
            brandDao.insertBrands(com.example.data.DefaultData.BRANDS)
        }
        if (backup.models.isNotEmpty()) {
            for (m in backup.models) modelDao.insertModel(m)
        } else {
            modelDao.insertModels(com.example.data.DefaultData.getModelsList())
        }
        if (backup.faults.isNotEmpty()) {
            for (f in backup.faults) faultDao.insertFault(f)
        } else {
            faultDao.insertFaults(com.example.data.DefaultData.FAULTS)
        }
        if (backup.repairTypes.isNotEmpty()) {
            for (rt in backup.repairTypes) repairTypeDao.insertRepairType(rt)
        } else {
            repairTypeDao.insertRepairTypes(com.example.data.DefaultData.REPAIR_TYPES)
        }
        for (p in backup.prices) priceDao.insertPrice(p)
        for (u in backup.universalRepairTypes) universalRepairTypeDao.insertUniversalRepairType(u)
        for (c in backup.customers) customerDao.insertCustomer(c)
        for (r in backup.repairs) repairDao.insertRepair(r)
        for (ri in backup.repairItems) repairItemDao.insertItem(ri)
        for (pm in backup.payments) paymentDao.insertPayment(pm)
        for (sh in backup.statusHistory) statusHistoryDao.insertHistory(sh)
        settingsDao.insertOrUpdate(backup.settings)
        if (backup.scannerLearning.isNotEmpty()) {
            scannerLearningDao.insertRecords(backup.scannerLearning)
        }
        // Stock restoration (prevent duplicates by name and map foreign keys safely)
        val stockItemIdMap = mutableMapOf<Long, Long>()
        for (item in backup.stockItems) {
            val existing = stockItemDao.findByName(item.name.trim())
            if (existing == null) {
                val newId = stockItemDao.insertStockItem(item.copy(id = 0))
                stockItemIdMap[item.id] = newId
            } else {
                stockItemDao.updateStockItem(item.copy(id = existing.id))
                stockItemIdMap[item.id] = existing.id
            }
        }
        for (conn in backup.stockConnections) {
            val mappedStockId = stockItemIdMap[conn.stockItemId] ?: conn.stockItemId
            val existingConn = stockRepairConnectionDao.findExactConnection(conn.brand.trim(), conn.model.trim(), conn.repairType.trim())
            if (existingConn == null) {
                stockRepairConnectionDao.insertConnection(conn.copy(id = 0, stockItemId = mappedStockId))
            } else {
                stockRepairConnectionDao.updateConnection(conn.copy(id = existingConn.id, stockItemId = mappedStockId))
            }
        }
        for (mov in backup.stockMovements) {
            val mappedStockId = stockItemIdMap[mov.stockItemId] ?: mov.stockItemId
            stockMovementDao.insertMovement(mov.copy(id = 0, stockItemId = mappedStockId))
        }

        // Restore Receipt Customization and Settings if included in backup
        if (backup.receiptCustomization != null) {
            val rc = backup.receiptCustomization

            // 1. Restore Logo Files to local storage
            val restoredLogoPathMap = mutableMapOf<String, String>()
            for (logo in rc.logos) {
                try {
                    if (logo.base64Data.isNotBlank() && logo.fileName.isNotBlank()) {
                        val bytes = Base64Helper.decode(logo.base64Data)
                        val savedPath = ReceiptPreferences.saveLogoBytes(context, logo.fileName, bytes)
                        if (savedPath != null) {
                            restoredLogoPathMap[logo.fileName] = savedPath
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("RepairRepository", "Failed restoring logo ${logo.fileName}", e)
                }
            }

            // 2. Remap logo file paths in Customer & Payment templates
            val resolvedCustTmpl = rc.customerTemplate?.let { tmpl ->
                val fixedElements = tmpl.elements.map { el ->
                    if (el.type == ReceiptElementType.IMAGE_LOGO && el.logoPath.isNotBlank()) {
                        val fName = File(el.logoPath).name
                        val newPath = restoredLogoPathMap[fName] ?: run {
                            val localFile = File(context.filesDir, "receipt_logos/$fName")
                            if (localFile.exists()) localFile.absolutePath else el.logoPath
                        }
                        el.copy(logoPath = newPath)
                    } else {
                        el
                    }
                }
                tmpl.copy(elements = fixedElements)
            }

            val resolvedPayTmpl = rc.paymentTemplate?.let { tmpl ->
                val fixedElements = tmpl.elements.map { el ->
                    if (el.type == ReceiptElementType.IMAGE_LOGO && el.logoPath.isNotBlank()) {
                        val fName = File(el.logoPath).name
                        val newPath = restoredLogoPathMap[fName] ?: run {
                            val localFile = File(context.filesDir, "receipt_logos/$fName")
                            if (localFile.exists()) localFile.absolutePath else el.logoPath
                        }
                        el.copy(logoPath = newPath)
                    } else {
                        el
                    }
                }
                tmpl.copy(elements = fixedElements)
            }

            // 3. Persist restored templates & configs to ReceiptPreferences
            if (resolvedCustTmpl != null) {
                ReceiptPreferences.saveCustomerTemplate(context, resolvedCustTmpl)
            }
            if (resolvedPayTmpl != null) {
                ReceiptPreferences.savePaymentTemplate(context, resolvedPayTmpl)
            }
            if (rc.customerConfig != null) {
                ReceiptPreferences.saveCustomerReceiptConfig(context, rc.customerConfig)
            }
            if (rc.paymentConfig != null) {
                ReceiptPreferences.savePaymentReceiptConfig(context, rc.paymentConfig)
            }

            // 4. Persist restored printer preferences
            PrinterPreferences.setAutoPrintEnabled(context, rc.autoPrintCustomerReceipt)
            PrinterPreferences.setCustomerReceiptDiagnosticMode(context, rc.customerReceiptDiagnosticMode)
            if (rc.p50sSettings != null) {
                PrinterPreferences.saveP50SSettings(context, rc.p50sSettings)
            }
        }
    }
}

data class BackupData(
    val repairs: List<RepairEntity>,
    val repairItems: List<RepairItemEntity>,
    val payments: List<PaymentEntity>,
    val statusHistory: List<StatusHistoryEntity>,
    val customers: List<CustomerEntity>,
    val brands: List<BrandEntity>,
    val models: List<ModelEntity>,
    val faults: List<FaultEntity>,
    val repairTypes: List<RepairTypeEntity>,
    val prices: List<PriceEntity>,
    val settings: AppSettingsEntity,
    val scannerLearning: List<ScannerLearningEntity> = emptyList(),
    val universalRepairTypes: List<UniversalRepairTypeEntity> = emptyList(),
    val stockItems: List<StockItemEntity> = emptyList(),
    val stockConnections: List<StockRepairConnectionEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val receiptCustomization: ReceiptBackupData? = null
)


