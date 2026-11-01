package com.example.ledger.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 网络状态检查：一期不做离线写入，写操作前先确认网络可用。 */
object NetworkUtils {

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
