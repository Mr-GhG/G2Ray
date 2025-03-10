package com.whitekod.g2ray.interfaces

import com.whitekod.g2ray.utils.V2rayConstants

interface StateListener {
    fun getConnectionState(): V2rayConstants.CONNECTION_STATES?

    fun getCoreState(): V2rayConstants.CORE_STATES?

    fun getDownloadSpeed(): Long

    fun getUploadSpeed(): Long
}