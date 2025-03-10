package com.whitekod.g2ray.core

import android.content.Context
import com.whitekod.g2ray.interfaces.Tun2SocksListener
import com.whitekod.g2ray.utils.V2rayConstants
import java.io.File
import java.util.Arrays
import java.util.Scanner

class Tun2SocksExecutor(private val tun2SocksListener: Tun2SocksListener) {
    private var tun2SocksProcess: Process? = null

    fun stopTun2Socks() {
        try {
            if (tun2SocksProcess != null) {
                tun2SocksProcess!!.destroy()
                tun2SocksProcess = null
            }
        } catch (ignore: Exception) {
        }
        tun2SocksListener.OnTun2SocksHasMassage(
            V2rayConstants.CORE_STATES.STOPPED,
            "T2S -> Tun2Socks Stopped."
        )
    }

    val isTun2SucksRunning: Boolean
        get() = tun2SocksProcess != null

    fun run(context: Context, socksPort: Int, localDnsPort: Int) {
        val tun2SocksCommands = ArrayList(
            Arrays.asList(
                File(context.applicationInfo.nativeLibraryDir, "libtun2socks.so").absolutePath,
                "--netif-ipaddr", "26.26.26.2",
                "--netif-netmask", "255.255.255.252",
                "--socks-server-addr", "127.0.0.1:$socksPort",
                "--tunmtu", "1500",
                "--sock-path", "sock_path",
                "--enable-udprelay",
                "--loglevel", "error"
            )
        )
        if (localDnsPort > 0 && localDnsPort < 65000) {
            tun2SocksCommands.add("--dnsgw")
            tun2SocksCommands.add("127.0.0.1:$localDnsPort")
        }
        tun2SocksListener.OnTun2SocksHasMassage(
            V2rayConstants.CORE_STATES.IDLE,
            "T2S Start Commands => $tun2SocksCommands"
        )
        try {
            val processBuilder = ProcessBuilder(tun2SocksCommands)
            tun2SocksProcess = processBuilder.directory(context.applicationContext.filesDir).start()
            Thread({
                val scanner = Scanner(tun2SocksProcess?.inputStream)
                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine()
                    tun2SocksListener.OnTun2SocksHasMassage(
                        V2rayConstants.CORE_STATES.RUNNING,
                        "T2S -> $line"
                    )
                }
            }, "t2s_output_thread").start()
            Thread({
                val scanner = Scanner(tun2SocksProcess?.errorStream)
                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine()
                    tun2SocksListener.OnTun2SocksHasMassage(
                        V2rayConstants.CORE_STATES.RUNNING,
                        "T2S => $line"
                    )
                }
            }, "t2s_error_thread").start()
            Thread({
                try {
                    tun2SocksProcess?.waitFor()
                } catch (e: InterruptedException) {
                    tun2SocksListener.OnTun2SocksHasMassage(
                        V2rayConstants.CORE_STATES.STOPPED,
                        "T2S -> Tun2socks Interrupted!$e"
                    )
                    tun2SocksProcess?.destroy()
                    tun2SocksProcess = null
                }
            }, "t2s_main_thread").start()
        } catch (e: Exception) {
            tun2SocksListener.OnTun2SocksHasMassage(
                V2rayConstants.CORE_STATES.IDLE,
                "Tun2socks Run Error =>> $e"
            )
            tun2SocksProcess!!.destroy()
            tun2SocksProcess = null
        }
    }
}