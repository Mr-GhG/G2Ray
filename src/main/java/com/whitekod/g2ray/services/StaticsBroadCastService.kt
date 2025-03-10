package com.whitekod.g2ray.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import androidx.annotation.RequiresApi
import com.whitekod.g2ray.interfaces.StateListener
import com.whitekod.g2ray.interfaces.TrafficListener
import com.whitekod.g2ray.utils.Utilities
import com.whitekod.g2ray.utils.V2rayConstants
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_CORE_STATE_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_DURATION_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_TYPE_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT

class StaticsBroadCastService(targetService: Context, stateListener: StateListener) {
    private var SERVICE_DURATION = "00:00:00"
    private var seconds = 0
    private var minutes = 0
    private var hours = 0
    private var totalDownload: Long = 0
    private var totalUpload: Long = 0
    private var uploadSpeed: Long = 0
    private var downloadSpeed: Long = 0
    private lateinit var countDownTimer: CountDownTimer
    var isTrafficStaticsEnabled: Boolean = false
    var isCounterStarted: Boolean
    var trafficListener: TrafficListener? = null

    init {
        resetCounter()
        isCounterStarted = false
        countDownTimer = object : CountDownTimer(604800000, 1000) {
            @SuppressLint("ObsoleteSdkInt")
            @RequiresApi(api = Build.VERSION_CODES.M)
            override fun onTick(millisUntilFinished: Long) {
                seconds++
                if (seconds == 59) {
                    minutes++
                    seconds = 0
                }
                if (minutes == 59) {
                    minutes = 0
                    hours++
                }
                if (hours == 23) {
                    hours = 0
                }
                if (isTrafficStaticsEnabled) {
                    downloadSpeed = stateListener.getDownloadSpeed()
                    uploadSpeed = stateListener.getUploadSpeed()
                    totalDownload = totalDownload + downloadSpeed
                    totalUpload = totalUpload + uploadSpeed
                    if (trafficListener != null) {
                        trafficListener?.onTrafficChanged(
                            uploadSpeed,
                            downloadSpeed,
                            totalUpload,
                            totalDownload
                        )
                    }
                }
                SERVICE_DURATION =
                    (Utilities.convertIntToTwoDigit(hours) + ":" + Utilities.convertIntToTwoDigit(
                        minutes
                    )).toString() + ":" + Utilities.convertIntToTwoDigit(seconds)
                sendBroadCast(targetService, stateListener)
            }

            override fun onFinish() {
                countDownTimer.cancel()
                StaticsBroadCastService(targetService, stateListener).start()
            }
        }
    }

    fun resetCounter() {
        SERVICE_DURATION = "00:00:00"
        seconds = 0
        minutes = 0
        hours = 0
        uploadSpeed = 0
        downloadSpeed = 0
    }

    fun start() {
        if (!isCounterStarted) {
            countDownTimer.start()
            isCounterStarted = true
        }
    }

    fun stop() {
        if (isCounterStarted) {
            isCounterStarted = false
            countDownTimer.cancel()
        }
    }

    fun sendBroadCast(targetService: Context, stateListener: StateListener) {
        val connection_info_intent: Intent = Intent(V2RAY_SERVICE_STATICS_BROADCAST_INTENT)
        connection_info_intent.setPackage(targetService.packageName)
        connection_info_intent.putExtra(
            SERVICE_CONNECTION_STATE_BROADCAST_EXTRA,
            stateListener.getConnectionState()
        )
        connection_info_intent.putExtra(
            SERVICE_CORE_STATE_BROADCAST_EXTRA,
            stateListener.getCoreState()
        )
        connection_info_intent.putExtra(SERVICE_DURATION_BROADCAST_EXTRA, SERVICE_DURATION)
        connection_info_intent.putExtra(
            SERVICE_TYPE_BROADCAST_EXTRA,
            targetService.javaClass.simpleName
        )
        connection_info_intent.putExtra(
            SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA,
            Utilities.parseTraffic(uploadSpeed, false, true)
        )
        connection_info_intent.putExtra(
            SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA,
            Utilities.parseTraffic(downloadSpeed, false, true)
        )
        connection_info_intent.putExtra(
            SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA,
            Utilities.parseTraffic(totalUpload, false, false)
        )
        connection_info_intent.putExtra(
            SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA,
            Utilities.parseTraffic(totalDownload, false, false)
        )
        targetService.sendBroadcast(connection_info_intent)
    }

    fun sendDisconnectedBroadCast(targetService: Context) {
        resetCounter()
        val connection_info_intent: Intent = Intent(V2RAY_SERVICE_STATICS_BROADCAST_INTENT)
        connection_info_intent.setPackage(targetService.packageName)
        connection_info_intent.putExtra(
            SERVICE_CONNECTION_STATE_BROADCAST_EXTRA,
            V2rayConstants.CONNECTION_STATES.DISCONNECTED
        )
        connection_info_intent.putExtra(
            SERVICE_CORE_STATE_BROADCAST_EXTRA,
            V2rayConstants.CORE_STATES.STOPPED
        )
        connection_info_intent.putExtra(
            SERVICE_TYPE_BROADCAST_EXTRA,
            targetService.javaClass.simpleName
        )
        connection_info_intent.putExtra(SERVICE_DURATION_BROADCAST_EXTRA, "00:00:00")
        connection_info_intent.putExtra(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA, "0.0 B/s")
        connection_info_intent.putExtra(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA, "0.0 B/s")
        connection_info_intent.putExtra(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA, "0.0 B")
        connection_info_intent.putExtra(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA, "0.0 B")
        targetService.sendBroadcast(connection_info_intent)
    }
}