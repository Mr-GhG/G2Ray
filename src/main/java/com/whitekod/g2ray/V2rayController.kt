@file:Suppress("DEPRECATION", "KotlinConstantConditions")

package com.whitekod.g2ray

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.whitekod.g2ray.core.V2rayCoreExecutor
import com.whitekod.g2ray.interfaces.LatencyDelayListener
import com.whitekod.g2ray.services.V2rayProxyService
import com.whitekod.g2ray.services.V2rayVPNService
import com.whitekod.g2ray.utils.Utilities
import com.whitekod.g2ray.utils.V2rayConfigs.connectionState
import com.whitekod.g2ray.utils.V2rayConfigs.currentConfig
import com.whitekod.g2ray.utils.V2rayConfigs.serviceMode
import com.whitekod.g2ray.utils.V2rayConstants
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.SERVICE_TYPE_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT
import libv2ray.Libv2ray
import java.util.Objects

object V2rayController {
    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null
    val stateUpdaterBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                connectionState = (Objects.requireNonNull<Bundle?>(intent.extras)
                    .getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA) as V2rayConstants.CONNECTION_STATES?)!!
                if (intent.extras!!.getString(SERVICE_TYPE_BROADCAST_EXTRA) == V2rayProxyService::class.java.getSimpleName()) {
                    serviceMode = V2rayConstants.SERVICE_MODES.PROXY_MODE
                } else {
                    serviceMode = V2rayConstants.SERVICE_MODES.VPN_MODE
                }
            } catch (ignore: Exception) {
            }
        }
    }

    fun init(activity: ComponentActivity, appIcon: Int, appName: String) {
        Utilities.copyAssets(activity)
        currentConfig.applicationIcon = appIcon
        currentConfig.applicationName = appName
        registerReceivers(activity)
        activityResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                startTunnel(activity)
            } else {
                Toast.makeText(activity, "Permission not granted.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceivers(activity: Activity) {
        try {
            activity.unregisterReceiver(stateUpdaterBroadcastReceiver)
        } catch (ignore: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                stateUpdaterBroadcastReceiver,
                IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT),
                Context.RECEIVER_EXPORTED
            )
        } else {
            activity.registerReceiver(
                stateUpdaterBroadcastReceiver,
                IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT)
            )
        }
    }

    fun getConnectionState(): V2rayConstants.CONNECTION_STATES { return connectionState }

    fun isPreparedForConnection(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    context!!,
                    permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        val vpnServicePrepareIntent = VpnService.prepare(context)
        return vpnServicePrepareIntent == null
    }

    private fun prepareForConnection(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(permission.POST_NOTIFICATIONS),
                    101
                )
                return
            }
        }
        val vpnServicePrepareIntent = VpnService.prepare(activity)
        if (vpnServicePrepareIntent != null) {
            activityResultLauncher!!.launch(vpnServicePrepareIntent)
        }
    }

    fun startV2ray(
        activity: Activity,
        remark: String,
        config: String,
        blockedApps: ArrayList<String>?
    ) {
        if (!Utilities.refillV2rayConfig(remark, config, blockedApps)) {
            return
        }
        if (!isPreparedForConnection(activity)) {
            prepareForConnection(activity)
        } else {
            startTunnel(activity)
        }
    }

    fun stopV2ray(context: Context) {
        val stopIntent = Intent(V2RAY_SERVICE_COMMAND_INTENT)
        stopIntent.setPackage(context.packageName)
        stopIntent.putExtra(
            V2RAY_SERVICE_COMMAND_EXTRA,
            V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE
        )
        context.sendBroadcast(stopIntent)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun getConnectedV2rayServerDelay(context: Context, latencyDelayCallback: LatencyDelayListener) {
        if (connectionState !== V2rayConstants.CONNECTION_STATES.CONNECTED) {
            latencyDelayCallback.OnResultReady(-1)
            return
        }
        val connectionLatencyBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val delay = Objects.requireNonNull<Bundle?>(intent.extras)
                        .getInt(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA)
                    latencyDelayCallback.OnResultReady(delay)
                } catch (ignore: Exception) {
                    latencyDelayCallback.OnResultReady(-1)
                }
                context.unregisterReceiver(this)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                connectionLatencyBroadcastReceiver,
                IntentFilter(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                connectionLatencyBroadcastReceiver,
                IntentFilter(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT)
            )
        }
        val get_delay_intent: Intent = Intent(V2RAY_SERVICE_COMMAND_INTENT)
        get_delay_intent.setPackage(context.packageName)
        get_delay_intent.putExtra(
            V2RAY_SERVICE_COMMAND_EXTRA,
            V2rayConstants.SERVICE_COMMANDS.MEASURE_DELAY
        )
        context.sendBroadcast(get_delay_intent)
    }

    fun getV2rayServerDelay(config: String): Long {
        return V2rayCoreExecutor.getConfigDelay(Utilities.normalizeV2rayFullConfig(config))
    }

    val coreVersion: String
        get() = Libv2ray.checkVersionX()

    fun toggleConnectionMode() {
        if (serviceMode === V2rayConstants.SERVICE_MODES.PROXY_MODE) {
            serviceMode = V2rayConstants.SERVICE_MODES.VPN_MODE
        } else {
            serviceMode = V2rayConstants.SERVICE_MODES.PROXY_MODE
        }
    }

    fun toggleTrafficStatics() {
        if (currentConfig.enableTrafficStatics) {
            currentConfig.enableTrafficStatics = false
            currentConfig.enableTrafficStaticsOnNotification = false
        } else {
            currentConfig.enableTrafficStatics = true
            currentConfig.enableTrafficStaticsOnNotification = true
        }
    }

    private fun startTunnel(context: Context) {
        val start_intent = if (serviceMode === V2rayConstants.SERVICE_MODES.PROXY_MODE) {
            Intent(context, V2rayProxyService::class.java)
        } else {
            Intent(context, V2rayVPNService::class.java)
        }
        start_intent.setPackage(context.packageName)
        start_intent.putExtra(
            V2RAY_SERVICE_COMMAND_EXTRA,
            V2rayConstants.SERVICE_COMMANDS.START_SERVICE
        )
        start_intent.putExtra(V2RAY_SERVICE_CONFIG_EXTRA, currentConfig)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            context.startForegroundService(start_intent)
        } else {
            context.startService(start_intent)
        }
    }

    @Deprecated("")
    fun IsPreparedForConnection(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    context!!,
                    permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        val vpnServicePrepareIntent = VpnService.prepare(context)
        return vpnServicePrepareIntent == null
    }

    @Deprecated("")
    fun StartV2ray(
        context: Context,
        remark: String,
        config: String,
        blockedApps: ArrayList<String>
    ) {
        if (!Utilities.refillV2rayConfig(remark, config, blockedApps)) {
            return
        }
        startTunnel(context)
    }

    @Deprecated("")
    fun StopV2ray(context: Context) {
        stopV2ray(context)
    }
}