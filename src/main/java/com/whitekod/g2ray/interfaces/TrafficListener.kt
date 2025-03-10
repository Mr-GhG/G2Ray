package com.whitekod.g2ray.interfaces

interface TrafficListener {
    fun onTrafficChanged(
        uploadSpeed: Long,
        downloadSpeed: Long,
        uploadedTraffic: Long,
        downloadedTraffic: Long
    )
}