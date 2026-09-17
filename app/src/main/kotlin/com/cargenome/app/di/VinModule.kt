package com.cargenome.app.di

import com.cargenome.app.data.vin.AssetWmiRegistry
import com.cargenome.app.data.vin.NhtsaVinDecoder
import com.cargenome.app.data.vin.OnlineVinDecoder
import com.cargenome.app.data.vin.RussianVdsVinDecoder
import com.cargenome.vin.OfflineVinDecoder
import com.cargenome.vin.WmiRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VinModule {

    @Provides
    @Singleton
    fun provideWmiRegistry(registry: AssetWmiRegistry): WmiRegistry = registry

    @Provides
    @Singleton
    fun provideOfflineVinDecoder(registry: WmiRegistry): OfflineVinDecoder = OfflineVinDecoder(registry)

    @Provides
    @dagger.multibindings.IntoSet
    fun provideRussianVdsVinDecoder(decoder: RussianVdsVinDecoder): OnlineVinDecoder = decoder

    @Provides
    @dagger.multibindings.IntoSet
    fun provideNhtsaVinDecoder(decoder: NhtsaVinDecoder): OnlineVinDecoder = decoder
}
