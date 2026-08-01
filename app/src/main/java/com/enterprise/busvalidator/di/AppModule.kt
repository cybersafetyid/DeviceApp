package com.enterprise.busvalidator.di

import com.enterprise.busvalidator.core.hardware.api.*
import com.enterprise.busvalidator.core.hardware.drivers.VendorDriverFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
