package com.whitekod.g2ray.services

import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.whitekod.g2ray.interfaces.TrafficListener
import com.whitekod.g2ray.utils.Utilities
import com.whitekod.g2ray.utils.V2rayConstants
import com.whitekod.g2ray.utils.V2rayConstants.V2RAY_SERVICE_OPENED_APPLICATION_INTENT
import java.util.Objects

class NotificationService(targetService: Service) {
    private var mNotificationManager: NotificationManager? = null
    private lateinit var notifcationBuilder: NotificationCompat.Builder
    var isNotificationOnGoing: Boolean = false
    private val NOTIFICATION_ID = 1

    var trafficListener: TrafficListener = object : TrafficListener {
        override fun onTrafficChanged(
            uploadSpeed: Long,
            downloadSpeed: Long,
            uploadedTraffic: Long,
            downloadedTraffic: Long
        ) {
            if (mNotificationManager != null) {
                if (isNotificationOnGoing) {
                    notifcationBuilder.setSubText(
                        ("Traffic ↓" + Utilities.parseTraffic(
                            downloadedTraffic,
                            false,
                            false
                        )) + "  ↑" + Utilities.parseTraffic(
                            uploadedTraffic,
                            false,
                            false
                        )
                    )
                    notifcationBuilder.setContentText(
                        ("""Tap to open application.
 Download : ↓${
                            Utilities.parseTraffic(
                                downloadSpeed,
                                false,
                                true
                            )
                        }""").toString() + " | Upload : ↑" + Utilities.parseTraffic(
                            uploadSpeed,
                            false,
                            true
                        )
                    )
                    mNotificationManager!!.notify(NOTIFICATION_ID, notifcationBuilder.build())
                }
            }
        }
    }


    init {
        val launchIntent = checkNotNull(
            targetService.packageManager.getLaunchIntentForPackage
                (targetService.applicationInfo.packageName)
        )
        launchIntent.setAction(V2RAY_SERVICE_OPENED_APPLICATION_INTENT)
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val notificationContentPendingIntent =
            PendingIntent.getActivity(targetService, 0, launchIntent, judgeForNotificationFlag())
        var notificationChannelID = ""
        var applicationName = "unknown_name"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val pm = targetService.applicationContext.packageManager
                val ai =
                    pm.getApplicationInfo(targetService.packageName, 0)
                applicationName = pm.getApplicationLabel(ai) as String
                notificationChannelID = createNotificationChannelID(targetService, applicationName)
            } catch (e: Exception) {
                notificationChannelID = createNotificationChannelID(targetService, applicationName)
            }
        }
        val disconnectIntent = Intent(targetService, targetService.javaClass)
        disconnectIntent.setPackage(targetService.packageName)
        disconnectIntent.putExtra(
            V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA,
            V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE
        )
        val disconnectPendingIntent =
            PendingIntent.getService(targetService, 0, disconnectIntent, judgeForNotificationFlag())
        notifcationBuilder = NotificationCompat.Builder(targetService, notificationChannelID)
        notifcationBuilder.setContentTitle("$applicationName Connecting...")
            .setSmallIcon(R.drawable.sym_def_app_icon)
            .setContentText("Connecting on process.\nTap to open application")
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(notificationContentPendingIntent)
            .setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)
            .addAction(R.drawable.btn_minus, "Disconnect", disconnectPendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notifcationBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        } else {
            notifcationBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFAULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            targetService.startForeground(
                NOTIFICATION_ID,
                notifcationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            targetService.startForeground(NOTIFICATION_ID, notifcationBuilder.build())
        }
        isNotificationOnGoing = true
    }

    private fun getNotificationManager(targetService: Service): NotificationManager? {
        if (mNotificationManager == null) {
            try {
                mNotificationManager =
                    targetService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            } catch (e: Exception) {
                return null
            }
        }
        return mNotificationManager
    }

    fun setConnectedNotification(remark: String, iconResource: Int) {
        if (mNotificationManager != null && notifcationBuilder != null) {
            if (isNotificationOnGoing) {
                notifcationBuilder.setSmallIcon(iconResource)
                notifcationBuilder.setContentTitle("Connected to $remark")
                notifcationBuilder.setContentText("Application connected successfully.\nTap to open Application.")
                mNotificationManager!!.notify(NOTIFICATION_ID, notifcationBuilder.build())
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannelID(
        targetService: Service,
        Application_name: String
    ): String {
        val notification_channel_id = "DEV7DEV_AXL_CH_ID"
        val notificationChannel = NotificationChannel(
            notification_channel_id,
            "$Application_name Background Service",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.lightColor = Color.BLUE
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notificationChannel.importance = NotificationManager.IMPORTANCE_NONE
        Objects.requireNonNull(getNotificationManager(targetService))!!.createNotificationChannel(notificationChannel)
        return notification_channel_id
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun judgeForNotificationFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    fun dismissNotification() {
        if (mNotificationManager != null) {
            isNotificationOnGoing = false
            mNotificationManager!!.cancel(NOTIFICATION_ID)
        }
    }
}