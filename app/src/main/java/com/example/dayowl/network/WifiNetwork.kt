package com.example.dayowl.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

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

    private var cm: ConnectivityManager? = null

    /** Called once from [com.example.dayowl.DayOwlApplication]. */
    fun track(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        this.cm = cm
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
        runCatching { (current ?: lookup())?.bindSocket(socket) }
    }

    /** For a socket opened before the callback has fired. */
    @Suppress("DEPRECATION") // allNetworks: the callback is the primary path, this is the fallback
    private fun lookup(): Network? {
        val cm = cm ?: return null
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /**
     * Every directed broadcast address this device has, plus 255.255.255.255.
     *
     * The limited broadcast alone is not enough: on the phone running the hotspot it follows the
     * default route - cellular, or nowhere - and never reaches the tether subnet. A subnet's own
     * broadcast address (e.g. 192.168.43.255) is routed out of the interface that owns it.
     */
    fun broadcastTargets(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (a in nif.interfaceAddresses) a.broadcast?.let { out.add(it) }
            }
        }
        out.add(InetAddress.getByName("255.255.255.255"))
        return out.toList()
    }
}
