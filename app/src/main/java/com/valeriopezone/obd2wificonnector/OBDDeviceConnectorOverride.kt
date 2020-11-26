package com.valeriopezone.obd2wificonnector

import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.sleep
import kotlin.system.measureTimeMillis

//i had to sostitute ObdDeviceConnection because it was not overridable
class ObdDeviceConnectionOverride(private val inputStream: InputStream, private val outputStream: OutputStream) {
    private val responseCache = mutableMapOf<ObdCommand, ObdRawResponse>()

    @Synchronized
    suspend fun run(
            command: ObdCommand,
            useCache: Boolean = false,
            delayTime: Long = 0
    ): ObdResponse = withContext(Dispatchers.IO) {
        val obdRawResponse =
                if (useCache && responseCache[command] != null) {
                    responseCache.getValue(command)
                } else {
                    runCommand(command, delayTime).also {
                        // Save response to cache
                        if (useCache) {
                            responseCache[command] = it
                        }
                    }
                }
        command.handleResponse(obdRawResponse)
    }

     fun runCommand(command: ObdCommand, delayTime: Long): ObdRawResponse {
        var rawData = ""
        val elapsedTime = measureTimeMillis {
            sendCommand(command, delayTime)
            rawData = readRawData()
        }
        return ObdRawResponse(rawData, elapsedTime)
    }

    private fun sendCommand(command: ObdCommand, delayTime: Long = 0) {
        outputStream.write("${command.rawCommand}\r".toByteArray())
        outputStream.flush()
        if (delayTime > 0) {
            sleep(delayTime)
        }
    }

     fun readRawData(): String {//fixed for project purposes
        val res = StringBuffer()
        var cc : Int = 0
         while ({ cc = inputStream.read(); cc }() != 62) {
             //Log.i("OBDCONNECTOR", "data : " + cc)
             res.append(cc.toChar())
         }
        return res.toString()


    }
}