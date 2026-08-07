package com.example.dayowl.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.model.SessionInfo
import com.example.dayowl.repository.SessionRepository

class DiscoveryManager(
    context: Context,
    private val sessionRepository: SessionRepository
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_dayowl._udp."

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startAdvertising(hostName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = hostName
            serviceType = this@DiscoveryManager.serviceType
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                // Log.d("DiscoveryManager", "Service registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("DiscoveryManager", "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun stopAdvertising() {
        registrationListener?.let {
            nsdManager.unregisterService(it)
            registrationListener = null
        }
    }

    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                // Log.d("DiscoveryManager", "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Log.d("DiscoveryManager", "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains("dayowl")) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("DiscoveryManager", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val session = SessionInfo(
                                hostName = resolvedInfo.serviceName,
                                ipAddress = resolvedInfo.host.hostAddress ?: "",
                                port = resolvedInfo.port, // This is the control port
                                sessionName = resolvedInfo.serviceName
                            )
                            val current = sessionRepository.discoveredSessions.value
                            if (current.none { it.hostName == session.hostName }) {
                                sessionRepository.updateDiscoveredSessions(current + session)
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val current = sessionRepository.discoveredSessions.value
                sessionRepository.updateDiscoveredSessions(current.filter { it.hostName != serviceInfo.serviceName })
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            nsdManager.stopServiceDiscovery(it)
            discoveryListener = null
        }
        sessionRepository.updateDiscoveredSessions(emptyList())
    }
}
