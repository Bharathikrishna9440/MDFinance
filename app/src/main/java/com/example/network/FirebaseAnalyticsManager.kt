package com.example.network

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object FirebaseAnalyticsManager {
    private const val TAG = "FirebaseAnalyticsMgr"
    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
            Log.i(TAG, "Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun logScreenView(screenName: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
            }
            analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Logged screen view event: $screenName")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging screen view", e)
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            analytics?.logEvent(eventName, params)
            Log.d(TAG, "Logged custom event: $eventName with parameters: $params")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging custom event: $eventName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun setUserProperty(name: String, value: String) {
        try {
            analytics?.setUserProperty(name, value)
            Log.d(TAG, "Set user property: $name = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user property: $name", e)
        }
    }
}
