package com.v2ray.ang.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.R
import com.v2ray.ang.resilience.ui.ResilienceUiHelper

/**
 * Main Activity of v2rayNG integrated with Network Resilience Diagnostics UI.
 */
class MainActivity : AppCompatActivity() {

    private var resilienceUiHelper: ResilienceUiHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize and attach Network Resilience UI Helper
        resilienceUiHelper = ResilienceUiHelper(this)
        resilienceUiHelper?.attach()
    }

    override fun onResume() {
        super.onResume()
        resilienceUiHelper?.attach()
    }

    override fun onPause() {
        super.onPause()
        resilienceUiHelper?.detach()
    }

    override fun onDestroy() {
        super.onDestroy()
        resilienceUiHelper?.detach()
        resilienceUiHelper = null
    }
}
