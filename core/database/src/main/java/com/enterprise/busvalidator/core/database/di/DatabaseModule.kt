package com.enterprise.busvalidator.core.database.di

import android.content.Context
import androidx.room.Room
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
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(db: ValidatorDatabase): TransactionDao {
        return db.transactionDao()
    }
}
