package com.example.seedintervalmonitoring

import android.os.Handler
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries


class IntervalPlotter(val context: MainActivity) {
    val series: LineGraphSeries<DataPoint> = LineGraphSeries()
    var lastX = 0

    private val mHandler: Handler = Handler()
    init {
        context.graph?.addSeries(series)
        val viewport = context.graph?.viewport
        viewport?.isScrollable = true
        viewport?.isScalable = true
        val glr = context.graph?.getGridLabelRenderer()
        glr?.padding = 52
    }

    fun appendData(yval:Double) {
        mHandler.post(object : Runnable {
            override fun run() {
                lastX++
                series.appendData(DataPoint(lastX.toDouble(), yval),
                    true, 300)
                context.graph?.refreshDrawableState()
            }
        })
        mHandler.post(object : Runnable {
            override fun run() {
                context.sensorHandler?.add_measurement(yval)
            }
        })
    }
}

class XYValue(var x: Double, var y: Double)