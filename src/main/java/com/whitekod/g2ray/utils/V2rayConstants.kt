package com.whitekod.g2ray.utils

object V2rayConstants {
    const val V2RAY_SERVICE_OPENED_APPLICATION_INTENT: String =
        "APP_OPEN_FROM_V2RAY_NOTIFICATION_INTENT"
    const val V2RAY_SERVICE_STATICS_BROADCAST_INTENT: String = "V2RAY_SERVICE_STATICS_INTENT"
    const val V2RAY_SERVICE_COMMAND_INTENT: String = "V2RAY_SERVICE_COMMAND_INTENT"
    const val V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT: String =
        "V2RAY_SERVICE_CURRENT_CONFIG_DELAY_INTENT"
    const val V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA: String =
        "V2RAY_SERVICE_CURRENT_CONFIG_DELAY_EXTRA"
    const val V2RAY_SERVICE_COMMAND_EXTRA: String = "V2RAY_SERVICE_COMMAND_EXTRA"
    const val V2RAY_SERVICE_CONFIG_EXTRA: String = "V2RAY_SERVICE_CONFIG_EXTRA"
    const val SERVICE_CONNECTION_STATE_BROADCAST_EXTRA: String = "CONNECTION_STATE_EXTRA"
    const val SERVICE_TYPE_BROADCAST_EXTRA: String = "SERVICE_TYPE_EXTRA"
    const val SERVICE_CORE_STATE_BROADCAST_EXTRA: String = "CORE_STATE_EXTRA"
    const val SERVICE_DURATION_BROADCAST_EXTRA: String = "SERVICE_DURATION_EXTRA"
    const val SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA: String = "UPLOAD_SPEED_EXTRA"
    const val SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA: String = "DOWNLOAD_SPEED_EXTRA"
    const val SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA: String = "DOWNLOADED_TRAFFIC_EXTRA"
    const val SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA: String = "UPLOADED_TRAFFIC_EXTRA"
    const val BYTE: Long = 1
    const val KILO_BYTE: Long = BYTE * 1024
    const val MEGA_BYTE: Long = KILO_BYTE * 1024
    const val GIGA_BYTE: Long = MEGA_BYTE * 1024

    const val DEFAULT_OUT_BOUND_PLACE_IN_FULL_JSON_CONFIG: String = "CONFIG_PROXY_OUTBOUND_PLACE"
    const val DEFAULT_FULL_JSON_CONFIG: String = """{
  "dns": {
    "hosts": {
      "domain:googleapis.cn": "googleapis.com"
    },
    "servers": [
      "1.1.1.1"
    ]
  },
  "inbounds": [
    {
      "listen": "127.0.0.1",
      "port": 10808,
      "protocol": "socks",
      "settings": {
        "auth": "noauth",
        "udp": true,
        "userLevel": 8
      },
      "sniffing": {
        "destOverride": [],
        "enabled": false
      },
      "tag": "socks"
    },
    {
      "listen": "127.0.0.1",
      "port": 10809,
      "protocol": "http",
      "settings": {
        "userLevel": 8
      },
      "tag": "http"
    }
  ],
  "log": {
    "loglevel": "error"
  },
  "outbounds": [
    $DEFAULT_OUT_BOUND_PLACE_IN_FULL_JSON_CONFIG,
    {
      "protocol": "freedom",
      "settings": {},
      "tag": "direct"
    },
    {
      "protocol": "blackhole",
      "settings": {
        "response": {
          "type": "http"
        }
      },
      "tag": "block"
    }
  ],
  "remarks": "test",
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {
        "ip": [
          "1.1.1.1"
        ],
        "outboundTag": "proxy",
        "port": "53",
        "type": "field"
      }
    ]
  }
}"""

    enum class SERVICE_MODES {
        VPN_MODE,
        PROXY_MODE
    }

    enum class SERVICE_COMMANDS {
        START_SERVICE,
        STOP_SERVICE,
        MEASURE_DELAY
    }

    enum class CONNECTION_STATES {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
    }

    enum class CORE_STATES {
        RUNNING,
        IDLE,
        STOPPED,
    }
}