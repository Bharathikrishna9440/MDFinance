package com.example.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

import com.example.util.SecureConfig

object FirebaseConnectionManager {
    private const val TAG = "FirebaseConn"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy {
        try {
            FirebaseDatabase.getInstance(SecureConfig.firebaseDatabaseUrl)
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    // Global flag to check if the background vault is unlocked
    var isCloudPipelineReady = false
        private set

    /**
     * Executes the invisible cryptographic handshake with Firebase
     */
    fun initializeSilentCloudConnection(onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val currentUser = auth.currentUser
        
        if (currentUser != null) {
            Log.d(TAG, "Existing silent session found. Pipeline Active. UID: ${currentUser.uid}")
            isCloudPipelineReady = true
            enableKeepSynced()
            onSuccess()
            return
        }

        Log.d(TAG, "Starting silent background authentication...")
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    Log.d(TAG, "Silent connection established successfully! Assigned App UID: ${user?.uid}")
                    isCloudPipelineReady = true
                    enableKeepSynced()
                    onSuccess()
                } else {
                    val errorMessage = task.exception?.message ?: "Unknown security handshake error"
                    Log.e(TAG, "Cloud pipeline connection failed: $errorMessage")
                    isCloudPipelineReady = false
                    onFailure(errorMessage)
                }
            }
    }

    /**
     * Offline Optimization: Tells Firebase to cache data locally on the tablet 
     * chip so it can survive rural route drops seamlessly.
     */
    private fun enableKeepSynced() {
        try {
            // Forces the database client to maintain a local copy of data automatically
            database.reference.keepSynced(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set disk persistence properties: ${e.message}")
        }
    }
}
