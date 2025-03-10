package com.whitekod.g2ray.utils

import com.whitekod.g2ray.model.V2rayConfigModel

object V2rayConfigs {
    var connectionState: V2rayConstants.CONNECTION_STATES = V2rayConstants.CONNECTION_STATES.DISCONNECTED
    var serviceMode: V2rayConstants.SERVICE_MODES = V2rayConstants.SERVICE_MODES.VPN_MODE
    var currentConfig: V2rayConfigModel = V2rayConfigModel()
}