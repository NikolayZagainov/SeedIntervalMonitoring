package com.example.seedintervalmonitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files


class sensorManager(val context:MainActivity) {
    var writePermissionGranted = false
    var readPermissionGranted = false
    var recordingStarted = false
        set(value) {
            field = value
            context.btn_save.isEnabled = !value
            context.btn_clear.isEnabled = !value
        }
    var fileCleared = false
        set(value) {
            field = value
            if(value)
            {
                context.btn_record.setText(context.getString(R.string.start_record))
                context.btn_save.isEnabled = false
            }
        }

    init {
        checkReadPermissions()
        checkWritePermissions()
        if(readPermissionGranted and writePermissionGranted)
        {
            init_records()
            init_startButton()
            init_clearButton()
            init_saveButton()
            setupUI(context.root)

        }
    }

    fun init_records()
    {
        createRecordDir()
        File(Environment.getExternalStorageDirectory(), folder).walk().forEach {
            if(Files.isRegularFile(it.toPath()))
            {
                add_item(it)
            }
        }
    }

    fun init_saveButton()
    {
        createRecordDir()
        context.btn_save.setOnClickListener {
            val src  = File(Environment.getExternalStorageDirectory(), tempFile)
            val filename = context.te_filename.text.toString()
            var to = File(Environment.getExternalStorageDirectory(),
                "${folder}/${filename}.csv")
            var index = 2
            while(to.exists())
            {
                to = File(Environment.getExternalStorageDirectory(),
                    "${folder}/${filename}(${index}).csv")
                index++
            }
            if(!src.renameTo(to))
            {
                Toast.makeText(context,
                    "Error! Invalid filename!",
                    Toast.LENGTH_LONG).show()
            }
            else
            {
                add_item(to)
                fileCleared = true
            }
        }
    }

    fun init_clearButton()
    {
        context.btn_clear.setOnClickListener {
            clearTemp()
        }
    }

    fun init_startButton()
    {
        checkTempDIr()
        val file = File(Environment.getExternalStorageDirectory(), tempFile)
        if(!file.exists())
        {
            fileCleared = true
        }
        else
        {
            context.btn_record.setText(context.getString(R.string.cont_record))
        }
        context.btn_record.setOnClickListener {
            startRecording()
        }

    }

    fun startRecording()
    {
        if(fileCleared)
        {
            val file = File(Environment.getExternalStorageDirectory(), tempFile)
            file.writeText("Intervals\n")
            fileCleared = false
        }
        context.btn_record.setText(context.getString(R.string.stop_record))
        recordingStarted = true
        context.btn_record.setOnClickListener {
            stopRecording()
        }
    }

    fun stopRecording()
    {
        context.btn_record.setText(context.getString(R.string.cont_record))
        recordingStarted = false
        context.btn_record.setOnClickListener {
            startRecording()
        }
    }

    fun add_item(item: File)
    {
        val view = context.layoutInflater.inflate(R.layout.record, null)
        view.tag = item.name
        view.findViewById<TextView>(R.id.tv_record).setText(item.name)
        view.findViewById<ImageButton>(R.id.btn_share).setOnClickListener {
            if (item.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider",
                    item
                )
                val intent = Intent(Intent.ACTION_SEND)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setType("*/*")
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent)
            }
        }
        view.findViewById<ImageButton>(R.id.btn_delete).setOnClickListener {
            item.delete()
            context.records.removeView(view)
        }
        context.records.addView(view)
    }

    fun add_measurement(value: Double)
    {
        if(recordingStarted)
        {
            val file = File(Environment.getExternalStorageDirectory(), tempFile)
            file.appendText("${value}\n")
        }
    }

    fun checkWritePermissions()
    {
        val storage = ContextCompat.checkSelfPermission(context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if(storage == PackageManager.PERMISSION_GRANTED)
        {
            writePermissionGranted = true
        }
        else
        {
            ActivityCompat.requestPermissions(context, arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), context.EXTERNAL_STORAGE_WRITE_CODE)
        }
    }

    fun checkReadPermissions()
    {
        val storage = ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_EXTERNAL_STORAGE)
        if(storage == PackageManager.PERMISSION_GRANTED)
        {
            readPermissionGranted = true
        }
        else
        {
            ActivityCompat.requestPermissions(context, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), context.EXTERNAL_STORAGE_READ_CODE)
        }
    }

    fun checkTempDIr()
    {
        val file = File(Environment.getExternalStorageDirectory(), tempDir)
        if(!file.exists())
        {
            file.mkdirs()
        }
    }

    fun clearTemp()
    {
        val directory = File(Environment.getExternalStorageDirectory(), tempDir)
        Files.walk(directory.toPath())
            .filter { Files.isRegularFile(it) }
            .map { it.toFile() }
            .forEach { it.delete() }
        context.intervalPlotter?.clearGraph()
        fileCleared =true
    }

    fun createRecordDir()
    {
        val file = File(Environment.getExternalStorageDirectory(), folder)
        if(!file.exists())
        {
            file.mkdirs()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupUI(view: View) {

        // Set up touch listener for non-text box views to hide keyboard.
        if (view !is EditText) {
            view.setOnTouchListener { v, event ->
                hideSoftKeyboard(this.context)
                when (event.action) {
                    else -> {}
                }
                false
            }
        }

        //If a layout container, iterate over children and seed recursion.
        if (view is ViewGroup) {
            for (i in 0 until (view as ViewGroup).childCount) {
                val innerView: View = (view as ViewGroup).getChildAt(i)
                setupUI(innerView)
            }
        }
    }

    fun hideSoftKeyboard(activity: Activity) {
        val inputMethodManager: InputMethodManager = activity.getSystemService(
            Activity.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        if (inputMethodManager.isAcceptingText()) {
            inputMethodManager.hideSoftInputFromWindow(
                activity.currentFocus!!.windowToken,
                0
            )
        }
    }

    companion object
    {
        val folder = "seedIntervals/records"
        val tempFile = "seedIntervals/temp/temp.csv"
        val tempDir = "seedIntervals/temp"
    }

}