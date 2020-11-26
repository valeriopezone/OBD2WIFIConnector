package com.valeriopezone.obd2wificonnector

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), CoroutineScope {

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)


        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            Snackbar.make(view, "Plug the ELM327 Adapter in your vehicle then turn ignition ON and press Connect", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        findViewById<Button>(R.id.splashscreen_btn_settings).setOnClickListener{
            Log.i("OBDCONNECTOR", "GO TO SETTINGS")
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.splashscreen_btn_connect).setOnClickListener{
            Log.i("OBDCONNECTOR", "CONNECT")


            //try connect to OBD interface

                launch {
                    findViewById<ProgressBar>(R.id.splashscreen_loader).setVisibility(View.VISIBLE)
                    delay(1500)
                    tryOBDInterfaceConnection()
                    findViewById<ProgressBar>(R.id.splashscreen_loader).setVisibility(View.GONE)
                }



        }


    }

    suspend fun tryOBDInterfaceConnection() {
        var context = this

        withContext(Dispatchers.IO) {
            var obdConn : OBDConnector = OBDConnector(context)

            if(obdConn.verify()){
                Log.i("OBDCONNECTOR", "OBD INTERFACE FOUND!")
                // goto dashboard
                Log.i("OBDCONNECTOR", "GO TO DASHBOARD")

                val intent = Intent(context, DashboardActivity::class.java)
                startActivity(intent)
            }else{
                Log.i("OBDCONNECTOR", "OBD INTERFACE NOT FOUND!")

                this@MainActivity.runOnUiThread(java.lang.Runnable {
                    val bld : AlertDialog.Builder = AlertDialog.Builder(context)
                    bld.setMessage(obdConn.getLastOBDError()).setCancelable(true).setNeutralButton(
                        "OK",
                        DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
                    val alert: AlertDialog = bld.create()
                    alert.show()
                })




            }
        }
    }

}





