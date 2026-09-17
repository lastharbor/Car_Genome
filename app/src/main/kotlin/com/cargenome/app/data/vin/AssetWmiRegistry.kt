package com.cargenome.app.data.vin

import android.content.Context
import com.cargenome.vin.JsonWmiRegistry
import com.cargenome.vin.WmiEntry
import com.cargenome.vin.WmiRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WMI table read from the APK's assets on first use.
 *
 * The file is around half a megabyte, so parsing it costs a beat. Callers must
 * stay off the main thread; [com.cargenome.app.data.vin.VinDecodingRepository]
 * is the one place that does the lookup, and it runs on the default dispatcher.
 */
@Singleton
class AssetWmiRegistry @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WmiRegistry {

    private val delegate: JsonWmiRegistry by lazy {
        context.assets.open(ASSET_NAME).bufferedReader().use { JsonWmiRegistry.parse(it.readText()) }
    }

    override fun lookup(vin: String): WmiEntry? = delegate.lookup(vin)

    private companion object {
        const val ASSET_NAME = "wmi.json"
    }
}
