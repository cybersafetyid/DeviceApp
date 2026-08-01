package com.enterprise.busvalidator.core.database.di

import com.enterprise.busvalidator.core.database.RoomTransactionLedgerWriter
import com.enterprise.busvalidator.core.database.TransactionLedgerWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LedgerBindingsModule {
    @Binds
    abstract fun bindTransactionLedgerWriter(
        writer: RoomTransactionLedgerWriter
    ): TransactionLedgerWriter
}
