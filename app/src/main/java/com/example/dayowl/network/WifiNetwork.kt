package com.example.dayowl.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.DatagramSocket

/**
 * The WiFi network, tracked so every socket can be pinned to it.
 *
 * A hotspot with no internet never becomes the process default network - Android leaves cellular
 * there - so an unbound socket can send over rmnet and never reach the LAN at all. Binding is best
 * effort: on the device that IS the access point there is no WiFi network object, and the socket
 * stays unbound, which is right, because the tether interface is then its only local route.
 */
object WifiNetwork {

    @Volatile
    private var current: Network? = null

    /** Called once from [com.example.dayowl.DayOwlApplication]. */
    fun track(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        // No NET_CAPABILITY_INTERNET in the request: a local-only hotspot never validates, and
        // requiring internet would make this null in exactly the case it exists for.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    current = network
                }

                override fun onLost(network: Network) {
                    if (current == network) current = null
                }
            })
        }
    }

    fun bind(socket: DatagramSocket) {
        runCatching { current?.bindSocket(socket) }
    }
}
