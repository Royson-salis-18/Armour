package com.armor.app.net

import com.armor.app.requireService
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler

/** Reports whether the phone currently has working internet. Callbacks arrive on [handler]'s thread. */
class ConnectivityMonitor(context: Context, private val handler: Handler, private val onChange: (online: Boolean) -> Unit) {
    private val cm = context.requireService<ConnectivityManager>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            onChange(
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }

        override fun onLost(network: Network) = onChange(false)
    }

    fun start() {
        onChange(false)
        cm.registerDefaultNetworkCallback(callback, handler)
    }

    fun stop() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
    }
}
