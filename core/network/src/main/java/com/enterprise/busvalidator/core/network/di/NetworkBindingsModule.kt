package com.enterprise.busvalidator.core.network.di

import com.enterprise.busvalidator.core.network.ApiHttpClient
import com.enterprise.busvalidator.core.network.LocationTelemetryApiFallback
import com.enterprise.busvalidator.core.network.LocationTelemetryMqttPublisher
import com.enterprise.busvalidator.core.network.MqttTelemetryClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {
    @Binds
    abstract fun bindLocationTelemetryMqttPublisher(
        client: MqttTelemetryClient
    ): LocationTelemetryMqttPublisher

    @Binds
    abstract fun bindLocationTelemetryApiFallback(
        client: ApiHttpClient
    ): LocationTelemetryApiFallback
}
