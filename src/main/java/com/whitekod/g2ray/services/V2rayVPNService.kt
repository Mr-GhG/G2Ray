package com.whitekod.g2ray.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import com.whitekod.g2ray.core.Tun2SocksExecutor
import com.whitekod.g2ray.core.V2rayCoreExecutor
import com.whitekod.g2ray.interfaces.StateListener
import com.whitekod.g2ray.interfaces.Tun2SocksListener
import com.whitekod.g2ray.interfaces.V2rayServicesListener
import com.whitekod.g2ray.model.V2rayConfigModel
import com.whitekod.g2ray.utils.V2rayConstants
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT
import java.io.File

class V2rayVPNService : VpnService(), V2rayServicesListener, Tun2SocksListener {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var tun2SocksExecutor: Tun2SocksExecutor? = null
    private var v2rayCoreExecutor: V2rayCoreExecutor? = null
    private var notificationService: NotificationService? = null
    private var staticsBroadCastService: StaticsBroadCastService? = null
    private var connectionState: V2rayConstants.CONNECTION_STATES = V2rayConstants.CONNECTION_STATES.DISCONNECTED
    private var currentConfig: V2rayConfigModel? = V2rayConfigModel()
    private var isServiceCreated = false
    private var isServiceSetupStarted = false

    private val serviceCommandBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val serviceCommand: V2rayConstants.SERVICE_COMMANDS =
                    intent.getSerializableExtra(V2RAY_SERVICE_COMMAND_EXTRA) as V2rayConstants.SERVICE_COMMANDS?
                        ?: return
                when (serviceCommand) {
                    V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE -> if (v2rayCoreExecutor != null) {
                        v2rayCoreExecutor!!.stopCore(true)
                    }

                    V2rayConstants.SERVICE_COMMANDS.MEASURE_DELAY -> if (v2rayCoreExecutor != null) {
                        v2rayCoreExecutor!!.broadCastCurrentServerDelay()
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
            val threadPolicy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(threadPolicy)
            tun2SocksExecutor = Tun2SocksExecutor(this)
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
                intent.getSerializableExtra(V2RAY_SERVICE_COMMAND_EXTRA) as V2rayConstants.SERVICE_COMMANDS?
                    ?: return super.onStartCommand(intent, flags, startId)
            when (serviceCommand) {
                V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE -> v2rayCoreExecutor!!.stopCore(true)
                V2rayConstants.SERVICE_COMMANDS.START_SERVICE -> {
                    currentConfig =
                        intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA) as V2rayConfigModel?
                    if (currentConfig == null) {
                        stopService()
                        //break
                    }
                    staticsBroadCastService!!.isTrafficStaticsEnabled =
                        currentConfig!!.enableTrafficStatics
                    if (currentConfig!!.enableTrafficStatics && currentConfig!!.enableTrafficStaticsOnNotification) {
                        staticsBroadCastService!!.trafficListener =
                            notificationService!!.trafficListener
                    }
                    v2rayCoreExecutor!!.startCore(currentConfig!!)
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

    override fun onRevoke() {
        stopService()
    }

    private fun setupService() {
        if (isServiceSetupStarted) {
            return
        } else {
            isServiceSetupStarted = true
        }
        try {
            if (tunnelInterface != null) {
                tunnelInterface!!.close()
            }
        } catch (ignore: Exception) {
        }
        val prepare_intent = prepare(this)
        if (prepare_intent != null) {
            return
        }
        val builder = builder
        try {
//            builder.addDisallowedApplication(getPackageName());
            tunnelInterface = builder.establish()
            var localDNSPort = 0
            if (currentConfig!!.enableLocalTunneledDNS) {
                localDNSPort = currentConfig!!.localDNSPort
            }
            tun2SocksExecutor?.run(this, currentConfig!!.localSocksPort, localDNSPort)
            sendFileDescriptor()
            if (tun2SocksExecutor?.isTun2SucksRunning == true) {
                connectionState = V2rayConstants.CONNECTION_STATES.CONNECTED
                notificationService!!.setConnectedNotification(
                    currentConfig!!.remark,
                    currentConfig!!.applicationIcon
                )
                staticsBroadCastService!!.start()
            }
        } catch (e: Exception) {
            Log.e(V2rayVPNService::class.java.simpleName, "setupFailed => ", e)
            stopService()
        }
    }

    private val builder: Builder
        get() {
            val builder: Builder = Builder()
            builder.setSession(currentConfig!!.remark)
            builder.setMtu(1500)
            builder.addAddress("26.26.26.1", 30)
            builder.addRoute("0.0.0.0", 0)
            if (currentConfig!!.enableLocalTunneledDNS) {
                builder.addDnsServer("26.26.26.2")
            } else {
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            if (currentConfig!!.blockedApplications != null) {
                for (i in 0 until currentConfig!!.blockedApplications!!.size) {
                    try {
                        builder.addDisallowedApplication(currentConfig!!.blockedApplications!![i])
                    } catch (ignore: Exception) {
                    }
                }
            }
            return builder
        }

    private fun sendFileDescriptor() {
        val localSocksFile = File(applicationContext.filesDir, "sock_path").absolutePath
        val tunFd = tunnelInterface!!.fileDescriptor
        Thread({
            var isSendFDSuccess = false
            for (sendFDTries in 0..4) {
                try {
                    Thread.sleep(50L * sendFDTries)
                    val clientLocalSocket = LocalSocket()
                    clientLocalSocket.connect(
                        LocalSocketAddress(
                            localSocksFile,
                            LocalSocketAddress.Namespace.FILESYSTEM
                        )
                    )
                    if (!clientLocalSocket.isConnected) {
                        Log.i(
                            "SOCK_FILE",
                            "Unable to connect to localSocksFile [$localSocksFile]"
                        )
                    } else {
                        Log.i("SOCK_FILE", "connected to sock file [$localSocksFile]")
                    }
                    val clientOutStream = clientLocalSocket.outputStream
                    clientLocalSocket.setFileDescriptorsForSend(arrayOf(tunFd))
                    clientOutStream.write(42)
                    //                    clientLocalSocket.setFileDescriptorsForSend(null);
                    clientLocalSocket.shutdownOutput()
                    clientLocalSocket.close()
                    isSendFDSuccess = true
                    break
                } catch (ignore: Exception) {
                }
            }
            if (!isSendFDSuccess) {
                Log.w("SendFDFailed", "Could`nt send file descriptor !")
            }
        }, "sendFd_Thread").start()
    }

    override fun onDestroy() {
        unregisterReceiver(serviceCommandBroadcastReceiver)
        super.onDestroy()
    }

    override fun onProtect(socket: Int): Boolean {
        return protect(socket)
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        setupService()
    }

    override fun stopService() {
        try {
            staticsBroadCastService!!.sendDisconnectedBroadCast(this)
            tun2SocksExecutor?.stopTun2Socks()
            staticsBroadCastService!!.stop()
            notificationService!!.dismissNotification()
            stopForeground(true)
            stopSelf()
            try {
                tunnelInterface!!.close()
            } catch (ignore: Exception) {
            }
        } catch (e: Exception) {
            Log.d(V2rayVPNService::class.java.simpleName, "stopService => ", e)
        }
    }

    override fun OnTun2SocksHasMassage(tun2SocksState: V2rayConstants.CORE_STATES?, newMessage: String?) {
        Log.i("TUN2SOCKS", newMessage!!)
    }
}