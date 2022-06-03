package com.example.seedintervalmonitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.jjoe64.graphview.GraphView

class MainActivity : AppCompatActivity() {
    // UI
    var btn_connect: Button? = null
    var btn_start_scan: Button? = null

    var graph: GraphView? = null
    lateinit var root: ConstraintLayout
    lateinit var te_filename: EditText
    lateinit var btn_record: Button
    lateinit var btn_clear: Button
    lateinit var btn_save: Button
    lateinit var records: LinearLayout
    var onMobile = false

    // bluetooth
    private val BLUETOOTH_PERMISSION_REQUEST_CODE: Int =   1
    val EXTERNAL_STORAGE_READ_CODE: Int =                  2
    val EXTERNAL_STORAGE_WRITE_CODE: Int =                 3

    private var txt_scan_status: TextView? = null
    private var txt_status: TextView? = null
    var meteringConnection: BluetoothConnect? = null
    var intervalPlotter: IntervalPlotter? = null

    var sensorHandler:sensorManager? = null

    //device

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isTablet(this)) {
            Log.i(
                "TheDeviceType",
                "Detected... You're using a Tablet",
            )
            setContentView(R.layout.activity_main)
        } else {
            Log.i(
                "TheDeviceType",
                "Detected... You're using a Mobile Phone",
            )
            onMobile = true
            setContentView(R.layout.mobile_activity_main)
        }

        initUI()
        initGraph()
        initializeBluetoothOrRequestPermission()

        sensorHandler = sensorManager(this)
        handleOrientation(resources.configuration.orientation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        handleOrientation(newConfig.orientation)
        super.onConfigurationChanged(newConfig)
    }

    fun handleOrientation(orientation: Int)
    {
        if(!onMobile) {
            return
        }
        val sensorLayout = findViewById<LinearLayout>(R.id.sensorLayout)

        if(orientation == ORIENTATION_LANDSCAPE)
        {
            sensorLayout.orientation = HORIZONTAL
            sensorLayout.layoutParams.height = 150.toPx()
            val scrv = sensorLayout.findViewById<ScrollView>(R.id.scrollRecords)
            scrv.layoutParams.height = MATCH_PARENT
            scrv.layoutParams.width = 0
            val cnst = sensorLayout.findViewById<ConstraintLayout>(R.id.constraintSensor)
            cnst.layoutParams.height = MATCH_PARENT
            cnst.layoutParams.width = 0
        }
        else
        {
            sensorLayout.orientation = VERTICAL
            sensorLayout.layoutParams.height = 300.toPx()
            val scrv = sensorLayout.findViewById<ScrollView>(R.id.scrollRecords)
            scrv.layoutParams.width = MATCH_PARENT
            scrv.layoutParams.height = 0
            val cnst = sensorLayout.findViewById<ConstraintLayout>(R.id.constraintSensor)
            cnst.layoutParams.width = MATCH_PARENT
            cnst.layoutParams.height = 0
        }
    }



    fun isTablet(context: Context): Boolean {
        return ((context.resources.configuration.screenLayout
                and Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE)
    }

    private fun initGraph()
    {
        intervalPlotter = IntervalPlotter(this)
    }

    private fun initUI()
    {
        //buttons
        btn_connect = findViewById(R.id.btn_connect)
        btn_start_scan = findViewById(R.id.btn_start_scan)
        graph = findViewById(R.id.graph)

        btn_clear = findViewById(R.id.btn_clear)
        btn_record = findViewById(R.id.btn_record)
        btn_save = findViewById(R.id.btn_save)

        //other
        root = findViewById(R.id.root)
        te_filename = findViewById(R.id.et_filename)
        records = findViewById(R.id.record_container)
    }


    // Bluetooth related ------------------------------------------------------------------
    private fun initializeBluetoothOrRequestPermission() {
        val requiredPermissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            initializeBluetooth()
        } else {
            requestPermissions(missingPermissions.toTypedArray(), BLUETOOTH_PERMISSION_REQUEST_CODE)
        }
    }

    private fun initializeBluetooth() {
        meteringConnection = BluetoothConnect( this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
    {
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    // all permissions are granted
                    initializeBluetooth()
                } else {
                    Toast.makeText(applicationContext,
                        "Error! Bluetooth permissions are not granted",
                        Toast.LENGTH_LONG).show()
                }
            }
            EXTERNAL_STORAGE_WRITE_CODE -> {
                if (grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    sensorHandler?.writePermissionGranted = true
                } else {
                    sensorHandler?.writePermissionGranted = false
                    Toast.makeText(applicationContext,
                        "Error! Write storage permissions are not granted",
                        Toast.LENGTH_LONG).show()
                }
            }
            EXTERNAL_STORAGE_READ_CODE -> {
                if (grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    sensorHandler?.readPermissionGranted = true
                } else {
                    sensorHandler?.readPermissionGranted = false
                    Toast.makeText(applicationContext,
                        "Error! Read storage permissions are not granted",
                        Toast.LENGTH_LONG).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    var isScanning = false
        set(value) {
            field = value
            runOnUiThread {
                btn_start_scan?.text = if (value) "Stop Scan" else "Start Scan"
                txt_scan_status?.text = if (value) "is scanning" else "not scanning"
            }
        }

    var deviceConnected = false
        set(value) {
            field = value
            runOnUiThread {
                btn_connect?.text = if (value) "Disconnect" else "Connect"
                btn_connect?.setEnabled(true)
                txt_status?.text = if (value) "is connected" else "disconnected"
            }
        }
}

fun Int.toPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()