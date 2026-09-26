package com.aielectronics.ble.android

import android.Manifest
import android.os.Build

object AndroidBlePermissionPolicy {

    fun runtimePermissions(apiLevel: Int = Build.VERSION.SDK_INT): List<String> =
        if (apiLevel >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}
