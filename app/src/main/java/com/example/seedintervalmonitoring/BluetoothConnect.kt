package com.example.seedintervalmonitoring

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.jjoe64.graphview.series.DataPoint
import java.util.*
import kotlin.concurrent.schedule


@SuppressLint("MissingPermission")
class BluetoothConnect(private val context: Context) {
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    var gattConnection: BluetoothGatt? = null
    private var theDevice: BluetoothDevice? = null

    private var the_timer_handler: TimerTask? = null
    private var readPending = false


    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    @SuppressLint("MissingPermission")
    private fun startBleScan()
    {
        val scanFilters: List<ScanFilter> = listOf(
            ScanFilter.Builder()
                .setDeviceName(THE_DEVICE_NAME)
                .build()
        )
        if (!(context as MainActivity).deviceConnected)
        {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            context.isScanning = true
        }
        else
        {
            Toast.makeText(context, "The device already connected disconnect first!",
                Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan()
    {
        bluetoothLeScanner?.stopScan(scanCallback)
        (context as MainActivity).isScanning = false
    }

    private val scanCallback = object : ScanCallback()
    {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            theDevice = result.device
            if(theDevice?.name == THE_DEVICE_NAME)
            {
                Log.i("ScanCallback", "Found BLE device! Name: " +
                        "${theDevice?.name ?: "Unnamed"}, " + "address: ${theDevice?.address}")
                stopBleScan()
                connectDevice()
            }
        }
    }

    // GATT callbacks ------------------------------------------------------------------------------
    private val gattCallback = object : BluetoothGattCallback()
    {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int)
        {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                if (newState == BluetoothProfile.STATE_CONNECTED)
                {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    (context as MainActivity).deviceConnected = true
                    try{
                        Toast.makeText(context, "Device connected!",
                            Toast.LENGTH_SHORT).show()
                    } catch (e: RuntimeException){ }

                    Handler(Looper.getMainLooper()).post {
                        gattConnection?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED)
                {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from " +
                            deviceAddress
                    )
                    gatt.close()
                    (context as MainActivity).deviceConnected = false
                    Toast.makeText(context, "Device disconnected!",
                        Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for " +
                        "$deviceAddress! Disconnecting...")
                gatt.close()
                (context as MainActivity).deviceConnected = false
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int)
        {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for" +
                        " ${device.address}")
                subscribeTossedMiss()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        onValueHaveRead(uuid, value)
                        Log.i("BluetoothGattCallback", "Read characteristic " +
                                "$uuid:\n${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for " +
                                "$uuid, error: $status")
                        Toast.makeText(context, "Failed to read ${charMap[uuid]}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
            readPending = false
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        onValueHasWritten(uuid, value)
                        Log.i("BluetoothGattCallback", "Wrote to characteristic " +
                                "$uuid | value: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback",
                            "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback",
                            "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed " +
                                "for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            with(descriptor)
            {
                when (status)
                {
                    BluetoothGatt.GATT_SUCCESS -> {
                        onDeviceConnected()
                        Log.i("BluetoothGattCallback", "Wrote to descriptor " +
                                "${this?.uuid} | value: ${this?.value?.toHexString()}")
                    }
                    else -> {
                        if(this?.uuid == seedIntervalDescUuid)
                        {
                            Toast.makeText(context, "Failed to subscribe to ${charMap[seedIntervalUuid]}",
                                Toast.LENGTH_SHORT).show()
                            subscribeTossedMiss()
                        }
                        Log.e("BluetoothGattCallback", "Descriptor write failed " +
                                "for ${this?.uuid}, error: $status")

                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                onValueChanged(uuid, value)
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: " +
                        value.toHexString()
                )
            }
        }

    }

    //-----------------------------------------------------------------------------------------------------------

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    @SuppressLint("MissingPermission")
    private fun subscribeTossedMiss() {

        val seedMissChar = gattConnection?.getService(meteringServiceUuid)?.getCharacteristic(
            seedIntervalUuid
        )

        gattConnection?.setCharacteristicNotification(seedMissChar, true)
        val descriptor: BluetoothGattDescriptor? = seedMissChar?.getDescriptor(seedIntervalDescUuid)
        descriptor?.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        gattConnection?.writeDescriptor(descriptor)
        gattConnection?.setCharacteristicNotification(seedMissChar, true)
    }


    @SuppressLint("MissingPermission")
    fun connectDevice()
    {
        if(theDevice != null)
        {
            gattConnection = theDevice?.connectGatt(this@BluetoothConnect.context,
                false, gattCallback)
            (context as MainActivity).btn_connect?.setEnabled(false)
            the_timer_handler?.cancel()
            the_timer_handler = Timer().schedule(1000) {
                context.btn_connect?.setEnabled(true)
            }
        }
        else
        {
            Toast.makeText(context, "No device saved, please scan!!",
                Toast.LENGTH_SHORT).show()
        }

    }

    companion object
    {
        private val THE_DEVICE_NAME: String = "SeedMonitoring"
        private val meteringServiceUuid: UUID = UUID.fromString("00009951-0000-1000-8000-00805f9b34fb")

        private val seedIntervalUuid: UUID = UUID.fromString("00001B01-0000-1000-8000-00805f9b34fb")
        private val seedIntervalDescUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private  val charMap = hashMapOf(
            seedIntervalUuid to "seed miss value"
        )
    }



//    private fun BluetoothGatt.printGattTable() {
//        if (services.isEmpty()) {
//            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
//            return
//        }
//        services.forEach { service ->
//            val characteristicsTable = service.characteristics.joinToString(
//                separator = "\n|--",
//                prefix = "|--"
//            ) { it.uuid.toString() }
//            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
//            )
//        }
//    }
    init {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter =  bluetoothManager?.getAdapter()
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
            if(bluetoothLeScanner == null)
            {
                Log.i("MDC", "bluetooth is not enabled")
            }
        }
        else
        {
            Log.i("MDC", "no support for bluetooth")
        }
        (context as MainActivity).btn_start_scan?.setOnClickListener {
            if (context.isScanning) {
                stopBleScan()
            } else {
                startBleScan()
            }
        }
        context.btn_connect?.setOnClickListener {
            if (context.deviceConnected) {
                gattConnection?.disconnect()
                gattConnection?.close()
                context.deviceConnected = false
            } else {
                connectDevice()
            }
        }
        startBleScan()
    }


    //------------------------------------CLEAN FUNCTIONS ------------------------------------------------
    private var seedInterval = 0U
        set(value) {
            field = value
            val pl = (context as MainActivity).intervalPlotter!!

            pl.appendData(seedInterval.toDouble())
        }


    fun onDeviceConnected()
    {
        //TODO
    }

    private fun onValueHaveRead(valId: UUID, value: ByteArray)
    {
        updateUIState(valId, value)
    }

    private fun onValueHasWritten(valId: UUID, value: ByteArray)
    {
        updateUIState(valId, value)
    }

    private  fun onValueChanged(valId: UUID, value: ByteArray)
    {
        updateUIState(valId, value)
    }

    private fun updateUIState(valId: UUID, value: ByteArray)
    {
        when(valId)
        {
            seedIntervalUuid -> {
                seedInterval = value.getUIntAt(0)
            }
        }
    }

    private fun ByteArray.getUIntAt(idx: Int) =
        ((this[idx + 3].toUInt() and 0xFFu) shl 24) or
        ((this[idx + 2].toUInt() and 0xFFu) shl 16) or
        ((this[idx + 1].toUInt() and 0xFFu) shl 8) or
        (this[idx].toUInt() and 0xFFu)

}


