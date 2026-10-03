package com.v2ray.ang.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.v2ray.ang.resilience.ResilienceServiceBinder

/**
 * Manages the connection lifecycle of the V2Ray VPN Service integrated with Resilience Monitoring.
 */
object V2RayServiceManager {
    private var resilienceBinder: ResilienceServiceBinder? = null

    /**
     * Starts the V2Ray VPN Service and initializes resilience monitoring.
     */
    fun startV2RayService(context: Context) {
        val intent = Intent(context, V2RayVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        startResilienceMonitoring(context)
    }

    /**
     * Stops the V2Ray VPN Service and shuts down resilience monitoring.
     */
    fun stopV2RayService(context: Context) {
        stopResilienceMonitoring()
        val intent = Intent(context, V2RayVpnService::class.java)
        context.stopService(intent)
    }

    /**
     * Starts continuous resilience monitoring.
     */
    fun startResilienceMonitoring(context: Context, socksPort: Int = 10808) {
        if (resilienceBinder == null) {
            resilienceBinder = ResilienceServiceBinder(context.applicationContext)
        }
        resilienceBinder?.start(socksPort)
    }

    /**
     * Stops resilience monitoring and releases references.
     */
    fun stopResilienceMonitoring() {
        resilienceBinder?.stop()
        resilienceBinder = null
    }
}
