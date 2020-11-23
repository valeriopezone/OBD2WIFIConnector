package com.valeriopezone.obd2wificonnector

import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import android.util.Log
import androidx.preference.PreferenceManager
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.at.DescribeProtocolNumberCommand
import com.github.eltonvs.obd.command.at.ResetAdapterCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.Runnable
import java.net.Socket

class OBDConnector(context: Context) {
    private var obd_wifi_url : String = ""
    private var obd_wifi_port : Int = 0
    private var lastError : String = ""
    private var obdSocket : Socket? = null
    private var obdConnection : ObdDeviceConnection? = null
    private var isConnected : Boolean = false
    private var firstRequestDone: Boolean  = false

    init{
        //load prefs
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        obd_wifi_url = prefs.getString("obd_wifi_url","192.168.0.10").toString()
        obd_wifi_port = Integer.parseInt(prefs.getString("obd_wifi_port","35000"))//prefs.getInt

        Log.i("OBDCONNECTOR", "LOADED CONF -----> " + obd_wifi_url + ":" + obd_wifi_port)


        // private var client : Socket = Socket("192.168.0.10", 35000)
                // private var obdConnection : ObdDeviceConnection = ObdDeviceConnection(this.client.getInputStream(), this.client.getOutputStream())
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build());
    }
    suspend fun connect() : Boolean{

        try{
            Log.i("OBDCONNECTOR", "CONNECT (KA) TO " + obd_wifi_url + " : " + obd_wifi_port)
            obdSocket = Socket( obd_wifi_url,obd_wifi_port)
            obdSocket!!.keepAlive = true


        } catch (t: Throwable) {
            lastError = t.message.toString()
            Log.i("OBDCONNECTOR EXCEPTION", "CONNECTING ERROR - WIFI INTERFACE :: " + lastError)
            return false;
        }
if(obdSocket!= null && obdSocket!!.isConnected()){
    try{
        Log.i("OBDCONNECTOR", "CONNECTING THROUGH OBD INTERFACE")
        obdConnection = ObdDeviceConnection(obdSocket!!.getInputStream(), obdSocket!!.getOutputStream())
        if(obdConnection != null){
            isConnected = true
            return true
        }
    } catch (t: Throwable) {
        close()
        lastError = t.message.toString()
        Log.i("OBDCONNECTOR EXCEPTION", "CONNECTING ERROR - OBD :: " + lastError)
        return false;
    }


}
        return false


    }

    fun close(){
        try{
            obdSocket?.close()
        }catch(T : Throwable){

        }

        isConnected = false
        firstRequestDone = false
    }

    suspend fun verify() : Boolean{
        Log.i("OBDCONNECTOR", "VERIFY")
        val tryConnect : Boolean  = connect()
        Log.i("OBDCONNECTOR", "VERIFY CHECK STATUS")
        if(tryConnect){
            Log.i("OBDCONNECTOR", "TRY CONNECTION")
            close()
            return true
        }
        return false
    }


    suspend fun execOBDCommand(cmd : ObdCommand) : String{
        Log.i("OBDCONNECTOR", "Launching CMD : " + cmd.rawCommand.toString())
        var res:String = ""
        if(obdSocket == null || !obdSocket?.isConnected()!!){//|| !isConnected
            Log.i("OBDCONNECTOR EXCEPTION", "CMD " + cmd.rawCommand.toString() + " IMPOSSIBLE - NO CONNECTION")
            when(connect()){
                true -> return execOBDCommand(cmd)
            }

        }else{

            try{
                val delay = 150L//if(firstRequestDone){150L}else{150L}
                val response = obdConnection?.run(cmd, false,delay)
                //Log.i("OBDCONNECTOR", "CMD " + cmd.rawCommand.toString() + " RES : " + response?.rawResponse.toString())
                if (response != null) {
                    Log.i("OBDCONNECTOR RES", cmd.toString() + " = " + response.rawResponse.toString() + "(" + response.formattedValue + ")")

                    res = response.value
                    if(!firstRequestDone){firstRequestDone = true}
                }
            } catch (t: Throwable) {
                Log.i("OBDCONNECTOR EXCEPTION", "NO RESPONSE FOR OBD COMMAND " + cmd.toString() +" :: " + t.message.toString())
            }
        }
        return res
    }

    fun isConnected() : Boolean{
        return isConnected
    }


    fun getLastOBDError() : String {
        return lastError
    }

}