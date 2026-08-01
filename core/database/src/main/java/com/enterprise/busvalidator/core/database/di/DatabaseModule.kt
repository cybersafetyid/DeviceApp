package com.enterprise.busvalidator.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enterprise.busvalidator.core.database.LocationLogDao
import com.enterprise.busvalidator.core.database.TransactionDao
import com.enterprise.busvalidator.core.database.ValidatorDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `location_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `deviceId` TEXT NOT NULL,
                    `recordedAtUtc` INTEGER NOT NULL,
                    `provider` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `altitudeMeters` REAL,
                    `accuracyMeters` REAL,
                    `verticalAccuracyMeters` REAL,
                    `bearingDegrees` REAL,
                    `bearingAccuracyDegrees` REAL,
                    `speedMetersPerSecond` REAL,
                    `speedAccuracyMetersPerSecond` REAL,
                    `elapsedRealtimeNanos` INTEGER NOT NULL,
                    `satelliteCount` INTEGER,
                    `isMock` INTEGER NOT NULL,
                    `isDelivered` INTEGER NOT NULL DEFAULT 0,
                    `deliveredAtUtc` INTEGER,
                    `deliveryTransport` TEXT,
                    `deliveryAttemptCount` INTEGER NOT NULL DEFAULT 0,
                    `lastDeliveryError` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_logs_recordedAtUtc` ON `location_logs` (`recordedAtUtc`)")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_location_logs_isDelivered_recordedAtUtc`
                ON `location_logs` (`isDelivered`, `recordedAtUtc`)
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideValidatorDatabase(@ApplicationContext context: Context): ValidatorDatabase {
        SQLiteDatabase.loadLibs(context)
        val passphrase = SQLiteDatabase.getBytes("EnterpriseBusValidatorSQLCipherPassphrase2026".toCharArray())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            ValidatorDatabase::class.java,
            "bus_validator_encrypted.db"
        )
        .openHelperFactory(factory)
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(db: ValidatorDatabase): TransactionDao {
        return db.transactionDao()
    }

    @Provides
    @Singleton
    fun provideLocationLogDao(db: ValidatorDatabase): LocationLogDao {
        return db.locationLogDao()
    }
}
