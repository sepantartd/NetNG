package com.v2ray.ang.resilience.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.v2ray.ang.resilience.ResilienceServiceBinder
import com.v2ray.ang.resilience.model.ConnectionHealthState

/**
 * UI Helper that dynamically attaches a clean floating Network Resilience Diagnostics banner
 * to the host [Activity] layout and updates metrics in real-time.
 */
class ResilienceUiHelper(private val activity: Activity) {

    private var bannerView: LinearLayout? = null
    private var stateTextView: TextView? = null
    private var metricsTextView: TextView? = null
    private var isReceiverRegistered = false

    private val resilienceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ResilienceServiceBinder.ACTION_RESILIENCE_UPDATE) {
                val stateName = intent.getStringExtra(ResilienceServiceBinder.EXTRA_HEALTH_STATE) ?: "HEALTHY"
                val rtt = intent.getLongExtra(ResilienceServiceBinder.EXTRA_LATENCY_MS, -1L)
                val jitter = intent.getLongExtra(ResilienceServiceBinder.EXTRA_JITTER_MS, 0L)
                val lossRate = intent.getFloatExtra(ResilienceServiceBinder.EXTRA_LOSS_RATE, 0.0f)
                val netType = intent.getStringExtra(ResilienceServiceBinder.EXTRA_NETWORK_TYPE) ?: "UNKNOWN"

                val healthState = try {
                    ConnectionHealthState.valueOf(stateName)
                } catch (e: Exception) {
                    ConnectionHealthState.HEALTHY
                }

                updateUi(healthState, rtt, jitter, lossRate, netType)
            }
        }
    }

    /**
     * Attaches the diagnostics banner dynamically to the activity's root view
     * and registers the broadcast receiver.
     */
    fun attach() {
        if (bannerView == null) {
            createBannerView()
        }
        registerReceiver()
        bannerView?.visibility = View.VISIBLE
    }

    /**
     * Unregisters the broadcast receiver and hides the banner view.
     */
    fun detach() {
        unregisterReceiver()
        bannerView?.visibility = View.GONE
    }

    private fun createBannerView() {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            paddingLeft = dpToPx(12)
            paddingRight = dpToPx(12)
            paddingTop = dpToPx(8)
            paddingBottom = dpToPx(8)
            elevation = dpToPx(6).toFloat()

            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E2C"))
                setStroke(dpToPx(1), Color.parseColor("#33334D"))
                cornerRadius = dpToPx(10).toFloat()
            }
            background = backgroundDrawable
        }

        stateTextView = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            text = "Network Resilience: Initializing..."
        }

        metricsTextView = TextView(activity).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#B0B0C0"))
            text = "Waiting for probes..."
        }

        container.addView(stateTextView)
        container.addView(metricsTextView)

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(80))
        }

        rootView.addView(container, layoutParams)
        bannerView = container
    }

    private fun updateUi(
        state: ConnectionHealthState,
        rtt: Long,
        jitter: Long,
        lossRate: Float,
        netType: String
    ) {
        val (colorHex, symbol) = when (state) {
            ConnectionHealthState.HEALTHY -> "#4CAF50" to "●"
            ConnectionHealthState.DEGRADED -> "#FFC107" to "▲"
            ConnectionHealthState.UNSTABLE -> "#FF9800" to "⚠️"
            ConnectionHealthState.DEAD -> "#F44336" to "✖"
        }

        val formattedLoss = String.format("%.1f%%", lossRate * 100)
        val rttText = if (rtt >= 0) "${rtt}ms" else "Timeout"

        stateTextView?.apply {
            text = "$symbol State: ${state.displayText} ($netType)"
            setTextColor(Color.parseColor(colorHex))
        }

        metricsTextView?.text = "RTT: $rttText | Jitter: ${jitter}ms | Loss: $formattedLoss"
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(ResilienceServiceBinder.ACTION_RESILIENCE_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                activity,
                resilienceReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            activity.registerReceiver(resilienceReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            activity.unregisterReceiver(resilienceReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        isReceiverRegistered = false
    }

    private fun dpToPx(dp: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
