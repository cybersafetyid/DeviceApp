package com.enterprise.busvalidator.di

import android.content.Context
import androidx.room.Room
import com.enterprise.busvalidator.core.database.TransactionDao
import com.enterprise.busvalidator.core.database.ValidatorDatabase
import com.enterprise.busvalidator.core.hardware.api.*
import com.enterprise.busvalidator.core.hardware.drivers.VendorDriverFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideValidatorDatabase(@ApplicationContext context: Context): ValidatorDatabase {
        return Room.databaseBuilder(
            context,
            ValidatorDatabase::class.java,
            "bus_validator_encrypted.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(db: ValidatorDatabase): TransactionDao {
        return db.transactionDao()
    }

    @Provides
    @Singleton
    fun provideLedDriver(factory: VendorDriverFactory): LedDriver = factory.createLedDriver()

    @Provides
    @Singleton
    fun provideAudioDriver(factory: VendorDriverFactory): AudioDriver = factory.createAudioDriver()

    @Provides
    @Singleton
    fun provideNfcDriver(factory: VendorDriverFactory): NfcDriver = factory.createNfcDriver()

    @Provides
    @Singleton
    fun provideSamDriver(factory: VendorDriverFactory): SamDriver = factory.createSamDriver()

    @Provides
    @Singleton
    fun provideSerialDriver(factory: VendorDriverFactory): SerialDriver = factory.createSerialDriver()

    @Provides
    @Singleton
    fun provideScannerDriver(factory: VendorDriverFactory): ScannerDriver = factory.createScannerDriver()

    @Provides
    @Singleton
    fun provideKeypadDriver(factory: VendorDriverFactory): KeypadDriver = factory.createKeypadDriver()
}
