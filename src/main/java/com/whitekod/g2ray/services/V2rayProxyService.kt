package com.whitekod.g2ray.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.whitekod.g2ray.core.V2rayCoreExecutor
import com.whitekod.g2ray.interfaces.StateListener
import com.whitekod.g2ray.interfaces.V2rayServicesListener
import com.whitekod.g2ray.model.V2rayConfigModel
import com.whitekod.g2ray.utils.V2rayConstants
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT

class V2rayProxyService : Service(), V2rayServicesListener {
    private var v2rayCoreExecutor: V2rayCoreExecutor? = null
    private var notificationService: NotificationService? = null
    private var staticsBroadCastService: StaticsBroadCastService? = null
    private var connectionState: V2rayConstants.CONNECTION_STATES = V2rayConstants.CONNECTION_STATES.DISCONNECTED
    private var currentConfig = V2rayConfigModel()
    private var isServiceCreated = false

    private val serviceCommandBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val serviceCommand: V2rayConstants.SERVICE_COMMANDS =
                    intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA) as V2rayConstants.SERVICE_COMMANDS?
                        ?: return
                when (serviceCommand) {
                    V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE -> if (v2rayCoreExecutor != null) {
                        v2rayCoreExecutor?.stopCore(true)
                    }

                    V2rayConstants.SERVICE_COMMANDS.MEASURE_DELAY -> if (v2rayCoreExecutor != null) {
                        v2rayCoreExecutor?.broadCastCurrentServerDelay()
                    }

                    else -> {}
                }
            } catch (ignore: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!isServiceCreated) {
            connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING
            v2rayCoreExecutor = V2rayCoreExecutor(this)
            notificationService = NotificationService(this)
            staticsBroadCastService = StaticsBroadCastService(this, object : StateListener {
                override fun getConnectionState(): V2rayConstants.CONNECTION_STATES {
                    return connectionState
                }

                override fun getCoreState(): V2rayConstants.CORE_STATES {
                    if (v2rayCoreExecutor == null) {
                        return V2rayConstants.CORE_STATES.IDLE
                    }
                    return v2rayCoreExecutor!!.getCoreState()
                }

                override fun getDownloadSpeed(): Long {
                    if (v2rayCoreExecutor == null) {
                        return -1
                    }
                    return v2rayCoreExecutor!!.getDownloadSpeed
                }

                override fun getUploadSpeed(): Long {
                    if (v2rayCoreExecutor == null) {
                        return -1
                    }
                    return v2rayCoreExecutor!!.getUploadSpeed
                }
            })
            isServiceCreated = true
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            val serviceCommand: V2rayConstants.SERVICE_COMMANDS =
                intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA) as V2rayConstants.SERVICE_COMMANDS?
                    ?: return super.onStartCommand(intent, flags, startId)
            when (serviceCommand) {
                V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE -> v2rayCoreExecutor?.stopCore(true)
                V2rayConstants.SERVICE_COMMANDS.START_SERVICE -> {
                    currentConfig =
                        (intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA) as V2rayConfigModel?)!!
                    if (currentConfig == null) {
                        stopService()
                        //break
                    }
                    staticsBroadCastService!!.isTrafficStaticsEnabled =
                        currentConfig.enableTrafficStatics
                    if (currentConfig.enableTrafficStatics && currentConfig.enableTrafficStaticsOnNotification) {
                        staticsBroadCastService!!.trafficListener =
                            notificationService!!.trafficListener
                    }
                    v2rayCoreExecutor?.startCore(currentConfig)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(
                            serviceCommandBroadcastReceiver,
                            IntentFilter(V2RAY_SERVICE_COMMAND_INTENT),
                            RECEIVER_EXPORTED
                        )
                    } else {
                        registerReceiver(
                            serviceCommandBroadcastReceiver,
                            IntentFilter(V2RAY_SERVICE_COMMAND_INTENT)
                        )
                    }
                    return START_STICKY
                }

                else -> onDestroy()
            }
        } catch (ignore: Exception) {
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        unregisterReceiver(serviceCommandBroadcastReceiver)
        super.onDestroy()
    }

    override fun onProtect(socket: Int): Boolean {
        return true
    }

    override fun getService(): Service { return this }

    override fun startService() {
        connectionState = V2rayConstants.CONNECTION_STATES.CONNECTED
        notificationService!!.setConnectedNotification(
            currentConfig.remark,
            currentConfig.applicationIcon
        )
        staticsBroadCastService!!.start()
    }

    override fun stopService() {
        try {
            staticsBroadCastService!!.sendDisconnectedBroadCast(this)
            staticsBroadCastService!!.stop()
            notificationService!!.dismissNotification()
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.d(V2rayProxyService::class.java.simpleName, "stopService => ", e)
        }
    }
}