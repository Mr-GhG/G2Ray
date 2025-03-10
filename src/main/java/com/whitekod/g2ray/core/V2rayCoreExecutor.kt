package com.whitekod.g2ray.core

import android.app.Service
import android.content.Intent
import android.os.Build
import android.util.Log
import com.whitekod.g2ray.interfaces.V2rayServicesListener
import com.whitekod.g2ray.model.V2rayConfigModel
import com.whitekod.g2ray.utils.Utilities
import com.whitekod.g2ray.utils.Utilities.getDeviceIdForXUDPBaseKey
import com.whitekod.g2ray.utils.Utilities.getUserAssetsPath
import com.whitekod.g2ray.utils.V2rayConstants
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT
import go.Seq
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import org.json.JSONObject

class V2rayCoreExecutor(targetService: Service) {
    private var coreState: V2rayConstants.CORE_STATES
    var v2rayServicesListener: V2rayServicesListener? = null


    val v2RayPoint: V2RayPoint = Libv2ray.newV2RayPoint(object : V2RayVPNServiceSupportsSet {
        override fun shutdown(): Long {
            try {
                if (v2rayServicesListener == null) {
                    Log.d(
                        V2rayCoreExecutor::class.java.simpleName,
                        "shutdown => can`t find initialed service."
                    )
                    return -1
                }
                v2rayServicesListener?.stopService()
                v2rayServicesListener = null
                return 0
            } catch (e: Exception) {
                Log.d(V2rayCoreExecutor::class.java.simpleName, "shutdown =>", e)
                return -1
            }
        }

        override fun prepare(): Long {
            return 0
        }

        override fun protect(l: Long): Boolean {
            if (v2rayServicesListener != null) return v2rayServicesListener!!.onProtect(l.toInt())
            return true
        }

        override fun onEmitStatus(l: Long, s: String): Long {
            return 0
        }

        override fun setup(s: String): Long {
            if (v2rayServicesListener != null) {
                try {
                    coreState = V2rayConstants.CORE_STATES.RUNNING
                    v2rayServicesListener?.startService()
                } catch (e: Exception) {
                    Log.d(V2rayCoreExecutor::class.java.simpleName, "setupFailed => ", e)
                    return -1
                }
            }
            return 0
        }
    }, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)


    init {
        this.v2rayServicesListener = targetService as V2rayServicesListener
        Seq.setContext(targetService)
        Libv2ray.initV2Env(
            getUserAssetsPath(targetService.applicationContext),
            getDeviceIdForXUDPBaseKey
        )
        coreState = V2rayConstants.CORE_STATES.IDLE
        Log.d(
            V2rayCoreExecutor::class.java.simpleName,
            "V2rayCoreExecutor -> New initialize from : " + targetService.javaClass.simpleName
        )
    }

    fun startCore(v2rayConfig: V2rayConfigModel) {
        try {
            stopCore(false)
            try {
                Libv2ray.testConfig(v2rayConfig.fullJsonConfig)
            } catch (testException: Exception) {
                coreState = V2rayConstants.CORE_STATES.STOPPED
                Log.d(
                    V2rayCoreExecutor::class.java.simpleName,
                    "startCore => v2ray json config not valid.",
                    testException
                )
                stopCore(true)
                return
            }
            v2RayPoint.configureFileContent = v2rayConfig.fullJsonConfig
            v2RayPoint.domainName =
                Utilities.normalizeIpv6(v2rayConfig.currentServerAddress) + ":" + v2rayConfig.currentServerPort
            v2RayPoint.runLoop(false)
        } catch (e: Exception) {
            Log.e(V2rayCoreExecutor::class.java.simpleName, "startCore =>", e)
        }
    }

    fun stopCore(shouldStopService: Boolean) {
        try {
            if (v2RayPoint.isRunning) {
                v2RayPoint.stopLoop()
                if (shouldStopService) {
                    v2rayServicesListener?.stopService()
                }
                coreState = V2rayConstants.CORE_STATES.STOPPED
            }
        } catch (e: Exception) {
            Log.d(V2rayCoreExecutor::class.java.simpleName, "stopCore =>", e)
        }
    }

    val getDownloadSpeed: Long
        get() = v2RayPoint.queryStats("block", "downlink") + v2RayPoint.queryStats(
            "proxy",
            "downlink"
        )

    val getUploadSpeed: Long
        get() = v2RayPoint.queryStats("block", "uplink") + v2RayPoint.queryStats("proxy", "uplink")

    fun getCoreState(): V2rayConstants.CORE_STATES {
        if (coreState === V2rayConstants.CORE_STATES.RUNNING) {
            if (!v2RayPoint.isRunning) {
                coreState = V2rayConstants.CORE_STATES.STOPPED
            }
            return coreState
        }
        return coreState
    }

    fun broadCastCurrentServerDelay() {
        try {
            if (v2rayServicesListener != null) {
                val serverDelay = v2RayPoint.measureDelay("").toInt()
                val serverDelayBroadcast: Intent =
                    Intent(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT)
                serverDelayBroadcast.setPackage(v2rayServicesListener?.getService()?.packageName)
                serverDelayBroadcast.putExtra(
                    V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA,
                    serverDelay
                )
                v2rayServicesListener?.getService()?.sendBroadcast(serverDelayBroadcast)
            }
        } catch (e: Exception) {
            Log.d(V2rayCoreExecutor::class.java.simpleName, "broadCastCurrentServerDelay => ", e)
            val serverDelayBroadcast: Intent =
                Intent(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT)
            serverDelayBroadcast.setPackage(v2rayServicesListener?.getService()?.packageName)
            serverDelayBroadcast.putExtra(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA, -1)
            v2rayServicesListener?.getService()?.sendBroadcast(serverDelayBroadcast)
        }
    }

    companion object {
        fun getConfigDelay(config: String): Long {
            try {
                val configJson = JSONObject(config)
                configJson.remove("routing")
                configJson.remove("dns")
                val routing = JSONObject()
                routing.put("domainStrategy", "IPIfNonMatch")
                configJson.put("routing", routing)
                configJson.put(
                    "dns", JSONObject(
                        """{
                            "hosts": {
                                "domain:googleapis.cn": "googleapis.com"
                            },
                            "servers": [
                                "1.1.1.1"
                            ]
                        }"""
                    )
                )
                return Libv2ray.measureOutboundDelay(configJson.toString(), "")
            } catch (jsonError: Exception) {
                Log.d(
                    V2rayCoreExecutor::class.java.simpleName,
                    "getCurrentServerDelay -> ",
                    jsonError
                )
                return -1
            }
        }
    }
}