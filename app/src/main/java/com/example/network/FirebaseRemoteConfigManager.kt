package com.example.network

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseRemoteConfigManager {
    private const val TAG = "FirebaseRemoteConfig"

    private val _welcomeMessage = MutableStateFlow("Welcome to MD Finance!")
    val welcomeMessage = _welcomeMessage.asStateFlow()

    private val _defaultInterestRate = MutableStateFlow(10)
    val defaultInterestRate = _defaultInterestRate.asStateFlow()

    private val _enableUpiFeatures = MutableStateFlow(true)
    val enableUpiFeatures = _enableUpiFeatures.asStateFlow()

    private val _lastFetchTime = MutableStateFlow(0L)
    val lastFetchTime = _lastFetchTime.asStateFlow()

    fun initializeAndFetch() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // hourly cache
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            // Define default local values
            val defaultValues = mapOf(
                "welcome_message" to "Welcome to MD Finance!",
                "default_interest_rate" to 10L,
                "enable_upi_features" to true
            )
            remoteConfig.setDefaultsAsync(defaultValues)

            // Trigger fetch and activation
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val updated = task.result
                        Log.i(TAG, "Config fetch completed. Was updated: $updated")
                        applyConfigValues(remoteConfig)
                    } else {
                        Log.w(TAG, "Config fetch failed. Using default configuration.")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Remote Config", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun applyConfigValues(remoteConfig: FirebaseRemoteConfig) {
        try {
            _welcomeMessage.value = remoteConfig.getString("welcome_message")
            _defaultInterestRate.value = remoteConfig.getLong("default_interest_rate").toInt()
            _enableUpiFeatures.value = remoteConfig.getBoolean("enable_upi_features")
            _lastFetchTime.value = System.currentTimeMillis()
            Log.d(TAG, "Successfully updated configurations from Remote Config: ${_welcomeMessage.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config values", e)
        }
    }
}
