package com.example.util

object OnlineTimeHelper {
    suspend fun getOnlineTimeOrLocal(): Pair<Long, Boolean> {
        // Strict Offline Mode: immediately return local system time and indicate it is not online verified
        return Pair(System.currentTimeMillis(), false)
    }
}
