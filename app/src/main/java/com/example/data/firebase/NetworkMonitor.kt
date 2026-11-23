package com.example.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.data.model.NetworkStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun currentStatus(): NetworkStatus = readNetworkStatus()

    fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        trySend(readNetworkStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(readNetworkStatus())
            }

            override fun onLost(network: Network) {
                trySend(readNetworkStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(readNetworkStatus())
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    private fun readNetworkStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkStatus(isConnected = false, transportLabel = "Offline", isMetered = false)
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkStatus(isConnected = false, transportLabel = "Offline", isMetered = false)

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val connected = hasInternet && isValidated

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> if (hasInternet) "Network" else "Offline"
        }

        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkStatus(
            isConnected = connected || hasInternet,
            transportLabel = if (hasInternet) transport else "Offline",
            isMetered = metered
        )
    }
}
