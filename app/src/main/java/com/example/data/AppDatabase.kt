package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BrandDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.FaultDao
import com.example.data.dao.ModelDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.PriceDao
import com.example.data.dao.RepairDao
import com.example.data.dao.RepairItemDao
import com.example.data.dao.RepairTypeDao
import com.example.data.dao.ScannerLearningDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.StatusHistoryDao
import com.example.data.dao.StockItemDao
import com.example.data.dao.StockMovementDao
import com.example.data.dao.StockRepairConnectionDao
import com.example.data.dao.UniversalRepairTypeDao
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
import kotlinx.coroutines.launch

@Database(
    entities = [
        RepairEntity::class,
        RepairItemEntity::class,
        PaymentEntity::class,
        StatusHistoryEntity::class,
        CustomerEntity::class,
        BrandEntity::class,
        ModelEntity::class,
        FaultEntity::class,
        RepairTypeEntity::class,
        PriceEntity::class,
        AppSettingsEntity::class,
        ScannerLearningEntity::class,
        UniversalRepairTypeEntity::class,
        StockItemEntity::class,
        StockMovementEntity::class,
        StockRepairConnectionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun repairDao(): RepairDao
    abstract fun repairItemDao(): RepairItemDao
    abstract fun paymentDao(): PaymentDao
    abstract fun statusHistoryDao(): StatusHistoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun brandDao(): BrandDao
    abstract fun modelDao(): ModelDao
    abstract fun faultDao(): FaultDao
    abstract fun repairTypeDao(): RepairTypeDao
    abstract fun priceDao(): PriceDao
    abstract fun universalRepairTypeDao(): UniversalRepairTypeDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun stockRepairConnectionDao(): StockRepairConnectionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun scannerLearningDao(): ScannerLearningDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN autoRegistrationSms INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN registrationSmsTemplate TEXT NOT NULL DEFAULT 'UDM MOBILE REPAIR\nJob No: {JOB_NO}\n\nWe have received your phone for repair.\nYour repair job has been successfully registered.\n\nTotal: Rs. {TOTAL}\nPaid: Rs. {PAID}\nBalance: Rs. {BALANCE}\n\nWe will contact you when your phone is ready.\nThank you for choosing UDM Mobile Repair.'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `scanner_learning_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `rawOcrText` TEXT NOT NULL,
                            `confirmedJobNumber` TEXT NOT NULL,
                            `detectedJobNumber` TEXT NOT NULL,
                            `confidence` REAL NOT NULL,
                            `preprocessingMethod` TEXT NOT NULL,
                            `characterSubstitutions` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `timesEncountered` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_scanner_learning_records_rawOcrText` ON `scanner_learning_records` (`rawOcrText`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_scanner_learning_records_confirmedJobNumber` ON `scanner_learning_records` (`confirmedJobNumber`)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN themeName TEXT NOT NULL DEFAULT 'UDM_CLASSIC'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN customThemeJson TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `universal_repair_types` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `defaultPrice` REAL NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_universal_repair_types_name` ON `universal_repair_types` (`name`)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `stock_items` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `quantity` INTEGER NOT NULL DEFAULT 0,
                            `purchasePrice` REAL NOT NULL DEFAULT 0.0,
                            `sellingPrice` REAL NOT NULL DEFAULT 0.0,
                            `supplier` TEXT NOT NULL DEFAULT '',
                            `notes` TEXT NOT NULL DEFAULT '',
                            `lowStockWarning` INTEGER NOT NULL DEFAULT 2,
                            `createdTimestamp` INTEGER NOT NULL DEFAULT 0,
                            `updatedTimestamp` INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_items_name` ON `stock_items` (`name`)")
                } catch (_: Exception) {}

                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `stock_movements` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `stockItemId` INTEGER NOT NULL,
                            `stockItemName` TEXT NOT NULL,
                            `quantityChange` INTEGER NOT NULL,
                            `resultingQuantity` INTEGER NOT NULL,
                            `date` TEXT NOT NULL,
                            `time` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `reason` TEXT NOT NULL,
                            `repairId` INTEGER DEFAULT NULL,
                            `repairItemId` INTEGER DEFAULT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_stockItemId` ON `stock_movements` (`stockItemId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_timestamp` ON `stock_movements` (`timestamp`)")
                } catch (_: Exception) {}

                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `stock_repair_connections` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `stockItemId` INTEGER NOT NULL,
                            `brand` TEXT NOT NULL DEFAULT '',
                            `model` TEXT NOT NULL DEFAULT '',
                            `repairType` TEXT NOT NULL,
                            `quantityUsed` INTEGER NOT NULL DEFAULT 1
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_repair_connections_stockItemId` ON `stock_repair_connections` (`stockItemId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_repair_connections_brand_model_repairType` ON `stock_repair_connections` (`brand`, `model`, `repairType`)")
                } catch (_: Exception) {}

                try {
                    db.execSQL("ALTER TABLE repair_items ADD COLUMN stockDeducted INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE repair_items ADD COLUMN stockItemId INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE repair_items ADD COLUMN stockDeductedQty INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "udm_repair_shop.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(false)

                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default data in background
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { database ->
                                    populateInitialData(database)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                // Also verify default data populated (e.g. if DB exists or opened)
                CoroutineScope(Dispatchers.IO).launch {
                    populateInitialDataIfNeeded(instance)
                }
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            db.brandDao().insertBrands(DefaultData.BRANDS)
            db.modelDao().insertModels(DefaultData.getModelsList())
            db.faultDao().insertFaults(DefaultData.FAULTS)
            db.repairTypeDao().insertRepairTypes(DefaultData.REPAIR_TYPES)
            db.priceDao().insertPrices(DefaultData.SAMPLE_PRICES)
            db.settingsDao().insertOrUpdate(AppSettingsEntity())
        }

        private suspend fun populateInitialDataIfNeeded(db: AppDatabase) {
            try {
                if (db.brandDao().getAllBrandsDirect().isEmpty()) {
                    populateInitialData(db)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
