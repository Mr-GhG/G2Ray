package com.whitekod.g2ray.interfaces

import com.whitekod.g2ray.utils.V2rayConstants

interface Tun2SocksListener {
    fun OnTun2SocksHasMassage(tun2SocksState: V2rayConstants.CORE_STATES?, newMessage: String?)
}