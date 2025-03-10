package com.whitekod.g2ray.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.whitekod.g2ray.utils.V2rayConfigs.currentConfig
import com.whitekod.g2ray.utils.V2rayConstants.DEFAULT_OUT_BOUND_PLACE_IN_FULL_JSON_CONFIG
import libv2ray.Libv2ray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.Objects

object Utilities {
    val getDeviceIdForXUDPBaseKey: String
        get() {
            val androidId = Settings.Secure.ANDROID_ID
            val androidIdBytes = androidId.toByteArray(StandardCharsets.UTF_8)
            return Base64.encodeToString(
                androidIdBytes.copyOf(32),
                Base64.NO_PADDING or Base64.URL_SAFE
            )
        }

    @Throws(IOException::class)
    fun CopyFiles(src: InputStream, dst: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.newOutputStream(dst.toPath()).use { out ->
                val buf = ByteArray(1024)
                var len: Int
                while ((src.read(buf).also { len = it }) > 0) {
                    out.write(buf, 0, len)
                }
            }
        } else {
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(1024)
                var len: Int
                while ((src.read(buf).also { len = it }) > 0) {
                    out.write(buf, 0, len)
                }
            }
        }
    }

    fun getUserAssetsPath(context: Context): String {
        val extDir = context.getExternalFilesDir("assets") ?: return ""
        return if (!extDir.exists()) {
            context.getDir("assets", 0).absolutePath
        } else {
            extDir.absolutePath
        }
    }

    fun copyAssets(context: Context) {
        val extFolder = getUserAssetsPath(context)
        try {
            val geo = "geosite.dat,geoip.dat"
            for (assets_obj in Objects.requireNonNull(context.assets.list(""))) {
                if (geo.contains(assets_obj!!)) {
                    CopyFiles(context.assets.open(assets_obj), File(extFolder, assets_obj))
                }
            }
        } catch (e: Exception) {
            Log.e(Utilities::class.java.simpleName, "copyAssets failed=>", e)
        }
    }

    fun convertIntToTwoDigit(value: Int): String {
        return if (value < 10) "0$value"
        else value.toString()
    }

    fun parseTraffic(bytes: Long, inBits: Boolean, isMomentary: Boolean): String {
        val value = if (inBits) bytes * 8 else bytes
        return if (value < V2rayConstants.KILO_BYTE) {
            String.format(
                Locale.getDefault(),
                "%.1f " + (if (inBits) "b" else "B") + (if (isMomentary) "/s" else ""),
                value.toDouble() // Преобразуем в Double
            )
        } else if (value < V2rayConstants.MEGA_BYTE) {
            String.format(
                Locale.getDefault(),
                "%.1f K" + (if (inBits) "b" else "B") + (if (isMomentary) "/s" else ""),
                (value.toDouble() / V2rayConstants.KILO_BYTE) // Преобразуем в Double
            )
        } else if (value < V2rayConstants.GIGA_BYTE) {
            String.format(
                Locale.getDefault(),
                "%.1f M" + (if (inBits) "b" else "B") + (if (isMomentary) "/s" else ""),
                (value.toDouble() / V2rayConstants.MEGA_BYTE) // Преобразуем в Double
            )
        } else {
            String.format(
                Locale.getDefault(),
                "%.2f G" + (if (inBits) "b" else "B") + (if (isMomentary) "/s" else ""),
                (value.toDouble() / V2rayConstants.GIGA_BYTE) // Преобразуем в Double
            )
        }
    }

    fun normalizeV2rayFullConfig(config: String): String {
        if (Libv2ray.isXrayURI(config)) {
            return V2rayConstants.DEFAULT_FULL_JSON_CONFIG.replace(
                DEFAULT_OUT_BOUND_PLACE_IN_FULL_JSON_CONFIG,
                Libv2ray.getXrayOutboundFromURI(config)
            )
        }
        return config
    }

    fun refillV2rayConfig(
        remark: String,
        config: String,
        blockedApplications: ArrayList<String>?
    ): Boolean {
        currentConfig.remark = remark
        currentConfig.blockedApplications = blockedApplications
        try {
            val config_json = JSONObject(normalizeV2rayFullConfig(config))
            try {
                val inbounds = config_json.getJSONArray("inbounds")
                for (i in 0 until inbounds.length()) {
                    try {
                        if (inbounds.getJSONObject(i).getString("protocol") == "socks") {
                            currentConfig.localSocksPort = inbounds.getJSONObject(i).getInt("port")
                        }
                    } catch (e: Exception) {
                        //ignore
                    }
                    try {
                        if (inbounds.getJSONObject(i).getString("protocol") == "http") {
                            currentConfig.localHttpPort = inbounds.getJSONObject(i).getInt("port")
                        }
                    } catch (e: Exception) {
                        //ignore
                    }
                }
            } catch (e: Exception) {
                Log.w(
                    Utilities::class.java.simpleName,
                    "startCore warn => can`t find inbound port of socks5 or http."
                )
                return false
            }
            try {
                currentConfig.currentServerAddress =
                    config_json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
                        .getJSONArray("vnext").getJSONObject(0).getString("address")
                currentConfig.currentServerPort =
                    config_json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
                        .getJSONArray("vnext").getJSONObject(0).getInt("port")
            } catch (e: Exception) {
                currentConfig.currentServerAddress =
                    config_json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
                        .getJSONArray("servers").getJSONObject(0).getString("address")
                currentConfig.currentServerPort =
                    config_json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
                        .getJSONArray("servers").getJSONObject(0).getInt("port")
            }
            try {
                if (config_json.has("policy")) {
                    config_json.remove("policy")
                }
                if (config_json.has("stats")) {
                    config_json.remove("stats")
                }
            } catch (ignore_error: Exception) {
                //ignore
            }
            if (currentConfig.enableTrafficStatics) {
                try {
                    val policy = JSONObject()
                    val levels = JSONObject()
                    levels.put(
                        "8",
                        JSONObject().put("connIdle", 300).put("downlinkOnly", 1).put("handshake", 4)
                            .put("uplinkOnly", 1)
                    )
                    val system = JSONObject().put("statsOutboundUplink", true)
                        .put("statsOutboundDownlink", true)
                    policy.put("levels", levels)
                    policy.put("system", system)
                    config_json.put("policy", policy)
                    config_json.put("stats", JSONObject())
                } catch (e: Exception) {
                    Log.e("log is here", e.toString())
                    currentConfig.enableTrafficStatics = false
                    //ignore
                }
            }
            currentConfig.fullJsonConfig = config_json.toString()
            return true
        } catch (e: Exception) {
            Log.e(Utilities::class.java.simpleName, "parseV2rayJsonFile failed => ", e)
            return false
        }
    }

    fun normalizeIpv6(address: String): String {
        return if (isIpv6Address(address) && !address.contains("[") && !address.contains("]")) {
            String.format("[%s]", address)
        } else {
            address
        }
    }

    fun isIpv6Address(address: String): Boolean {
        val tmp = address.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return tmp.size > 2
    }
}