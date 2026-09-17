package com.cargenome.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface NetworkMonitor {
    fun isOnline(): Boolean
}

/**
 * Asks for a validated connection rather than just an attached one, so a Wi-Fi
 * network with a captive portal is treated as offline. That matters here: the
 * point of checking is to skip a lookup that would only hang and fail.
 */
@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkMonitor {

    override fun isOnline(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
