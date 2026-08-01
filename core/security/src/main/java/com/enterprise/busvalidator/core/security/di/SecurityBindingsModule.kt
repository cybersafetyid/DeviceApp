package com.enterprise.busvalidator.core.security.di

import com.enterprise.busvalidator.core.security.SharedPreferencesTimeAnchorStore
import com.enterprise.busvalidator.core.security.TimeAnchorStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityBindingsModule {
    @Binds
    abstract fun bindTimeAnchorStore(
        store: SharedPreferencesTimeAnchorStore
    ): TimeAnchorStore
}
