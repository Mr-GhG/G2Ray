package com.whitekod.g2ray.model

import java.io.Serializable

class V2rayConfigModel : Serializable {
    var applicationName: String? = null
    var applicationIcon: Int = 0
    var remark: String = ""
    var blockedApplications: ArrayList<String>? = null
    var fullJsonConfig: String? = null
    var currentServerAddress: String = ""
    var currentServerPort: Int = 443
    var localSocksPort: Int = 10808
    var localHttpPort: Int = 10809
    var localDNSPort: Int = 1053
    var enableTrafficStatics: Boolean = true
    var enableTrafficStaticsOnNotification: Boolean = true
    var enableLocalTunneledDNS: Boolean = false
}