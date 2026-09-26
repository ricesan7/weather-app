package com.aielectronics.ble.android

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidBlePermissionPolicyTest {

    @Test
    fun `android 12 and later use nearby device permissions`() {
        val permissions = AndroidBlePermissionPolicy.runtimePermissions(31)

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            permissions,
        )
    }

    @Test
    fun `android 11 and earlier use legacy scan location permission`() {
        val permissions = AndroidBlePermissionPolicy.runtimePermissions(30)

        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            permissions,
        )
    }

    @Test
    fun `runtime gatt uuids are distinct`() {
        assertTrue(BleGattProfile.SERVICE_UUID != BleGattProfile.WRITE_UUID)
        assertTrue(BleGattProfile.SERVICE_UUID != BleGattProfile.NOTIFY_UUID)
        assertTrue(BleGattProfile.WRITE_UUID != BleGattProfile.NOTIFY_UUID)
    }
}
