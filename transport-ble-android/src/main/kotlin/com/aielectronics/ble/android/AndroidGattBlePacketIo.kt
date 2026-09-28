package com.aielectronics.ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.aielectronics.ble.BleDisconnectedException
import com.aielectronics.ble.BlePacketCodec
import com.aielectronics.ble.BlePacketIo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AndroidGattBlePacketIo(
    context: Context,
    private val device: BluetoothDevice,
    private val profile: BleGattProfile = BleGattProfile(),
) : BlePacketIo {

    private val appContext = context.applicationContext

    @Volatile
    override var isConnected: Boolean = false
        private set

    @Volatile
    private var negotiatedMtu: Int = 23

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val connectLatch = AtomicReference<CountDownLatch?>()
    private val servicesLatch = AtomicReference<CountDownLatch?>()
    private val mtuLatch = AtomicReference<CountDownLatch?>()
    private val descriptorLatch = AtomicReference<CountDownLatch?>()
    private val writeLatch = AtomicReference<CountDownLatch?>()

    @Volatile private var connectionStatus: Int = BluetoothGatt.GATT_FAILURE
    @Volatile private var servicesStatus: Int = BluetoothGatt.GATT_FAILURE
    @Volatile private var mtuStatus: Int = BluetoothGatt.GATT_FAILURE
    @Volatile private var descriptorStatus: Int = BluetoothGatt.GATT_FAILURE
    @Volatile private var writeStatus: Int = BluetoothGatt.GATT_FAILURE

    private val notificationQueue = LinkedBlockingQueue<ByteArray>()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            connectionStatus = status
            isConnected =
                status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED
            connectLatch.get()?.countDown()

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                writeLatch.get()?.countDown()
                descriptorLatch.get()?.countDown()
                mtuLatch.get()?.countDown()
                servicesLatch.get()?.countDown()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            servicesStatus = status
            servicesLatch.get()?.countDown()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mtuStatus = status
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            }
            mtuLatch.get()?.countDown()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            descriptorStatus = status
            descriptorLatch.get()?.countDown()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writeStatus = status
            writeLatch.get()?.countDown()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == profile.notifyCharacteristicUuid) {
                notificationQueue.offer(value.copyOf())
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == profile.notifyCharacteristicUuid
            ) {
                characteristic.value?.let { value ->
                    notificationQueue.offer(value.copyOf())
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(): Result<Unit> = runCatching {
        if (isConnected && gatt != null) return@runCatching

        closeGatt()

        val connectionWait = CountDownLatch(1)
        connectLatch.set(connectionWait)
        connectionStatus = BluetoothGatt.GATT_FAILURE

        val newGatt = device.connectGatt(
            appContext,
            false,
            callback,
            BluetoothDevice.TRANSPORT_LE,
        ) ?: error("connectGatt returned null")

        gatt = newGatt

        check(connectionWait.await(profile.connectTimeoutMs, TimeUnit.MILLISECONDS)) {
            "BLE connection timed out"
        }
        ensureConnected("BLE connection failed")

        val servicesWait = CountDownLatch(1)
        servicesLatch.set(servicesWait)
        servicesStatus = BluetoothGatt.GATT_FAILURE
        check(newGatt.discoverServices()) {
            "BLE service discovery did not start"
        }
        check(servicesWait.await(profile.operationTimeoutMs, TimeUnit.MILLISECONDS)) {
            "BLE service discovery timed out"
        }
        ensureConnected("BLE disconnected during service discovery")
        check(servicesStatus == BluetoothGatt.GATT_SUCCESS) {
            "BLE service discovery failed: $servicesStatus"
        }

        val service = newGatt.getService(profile.serviceUuid)
            ?: error("Runtime BLE service not found")
        writeCharacteristic =
            service.getCharacteristic(profile.writeCharacteristicUuid)
                ?: error("Runtime BLE write characteristic not found")
        notifyCharacteristic =
            service.getCharacteristic(profile.notifyCharacteristicUuid)
                ?: error("Runtime BLE notify characteristic not found")

        enableNotifications(
            gatt = newGatt,
            characteristic = notifyCharacteristic
                ?: error("Notify characteristic missing"),
        )

        negotiateMtu(newGatt)
    }.onFailure {
        closeGatt()
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        closeGatt()
    }

    override fun maxPacketBytes(): Int =
        (negotiatedMtu - 3)
            .coerceAtLeast(BlePacketCodec.HEADER_SIZE + 1)

    @Synchronized
    @SuppressLint("MissingPermission")
    override fun transact(
        requestPackets: List<ByteArray>,
    ): Result<List<ByteArray>> = runCatching {
        val currentGatt = gatt ?: throw BleDisconnectedException()
        val write = writeCharacteristic ?: throw BleDisconnectedException()

        ensureConnected()
        notificationQueue.clear()

        requestPackets.forEach { packet ->
            val wait = CountDownLatch(1)
            writeLatch.set(wait)
            writeStatus = BluetoothGatt.GATT_FAILURE

            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(
                    write,
                    packet,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                write.value = packet
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(write)
            }

            check(started) { "BLE characteristic write did not start" }
            check(wait.await(profile.operationTimeoutMs, TimeUnit.MILLISECONDS)) {
                "BLE characteristic write timed out"
            }
            ensureConnected()
            check(writeStatus == BluetoothGatt.GATT_SUCCESS) {
                "BLE characteristic write failed: $writeStatus"
            }
        }

        collectResponsePackets()
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        check(gatt.setCharacteristicNotification(characteristic, true)) {
            "BLE local notification enable failed"
        }

        val descriptor = characteristic.getDescriptor(BleGattProfile.CCCD_UUID)
            ?: error("Runtime BLE CCCD not found")

        val wait = CountDownLatch(1)
        descriptorLatch.set(wait)
        descriptorStatus = BluetoothGatt.GATT_FAILURE

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        check(started) { "BLE CCCD write did not start" }
        check(wait.await(profile.operationTimeoutMs, TimeUnit.MILLISECONDS)) {
            "BLE CCCD write timed out"
        }
        ensureConnected()
        check(descriptorStatus == BluetoothGatt.GATT_SUCCESS) {
            "BLE CCCD write failed: $descriptorStatus"
        }
    }

    @SuppressLint("MissingPermission")
    private fun negotiateMtu(gatt: BluetoothGatt) {
        val wait = CountDownLatch(1)
        mtuLatch.set(wait)
        mtuStatus = BluetoothGatt.GATT_FAILURE

        if (!gatt.requestMtu(profile.requestedMtu)) {
            return
        }

        if (!wait.await(profile.operationTimeoutMs, TimeUnit.MILLISECONDS)) {
            return
        }

        ensureConnected()

        if (mtuStatus != BluetoothGatt.GATT_SUCCESS) {
            negotiatedMtu = 23
        }
    }

    private fun collectResponsePackets(): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        val deadlineNanos =
            System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(profile.responseTimeoutMs)

        while (true) {
            ensureConnected()

            val remainingNanos = deadlineNanos - System.nanoTime()
            check(remainingNanos > 0) {
                "BLE response timed out"
            }

            val packet = notificationQueue.poll(
                remainingNanos,
                TimeUnit.NANOSECONDS,
            ) ?: error("BLE response timed out")

            packets += packet

            val decoded = BlePacketCodec.decode(packet)
            if (decoded.end) {
                return packets
            }
        }
    }

    private fun ensureConnected(
        message: String = "BLE link disconnected",
    ) {
        if (!isConnected) {
            throw BleDisconnectedException(message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        isConnected = false
        writeCharacteristic = null
        notifyCharacteristic = null
        notificationQueue.clear()
        negotiatedMtu = 23

        gatt?.let { current ->
            runCatching { current.disconnect() }
            runCatching { current.close() }
        }
        gatt = null
    }
}
