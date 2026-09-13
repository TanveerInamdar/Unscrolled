package com.foxtrotalpha.reelsblocker.health

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.foxtrotalpha.reelsblocker.R

/**
 * Shown by Health Connect when the user taps "privacy policy" during permission flow.
 * Also satisfies Play Store requirements for health data permissions.
 */
class HealthPermissionsRationaleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_permissions_rationale)
    }
}
