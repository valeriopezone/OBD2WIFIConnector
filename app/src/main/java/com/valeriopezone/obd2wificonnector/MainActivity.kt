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
        //setSupportActionBar(findViewById(R.id.toolbar))


        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
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
            if(false){
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
            }else{
                launch {
                    findViewById<ProgressBar>(R.id.splashscreen_loader).setVisibility(View.VISIBLE)
                    delay(1500)
                    tryOBDInterfaceConnection()
                    findViewById<ProgressBar>(R.id.splashscreen_loader).setVisibility(View.GONE)
                }
            }


        /*

        */

        }

        //findViewById<Button>(R.id.splashscreen_btn_instructions).setOnClickListener {
            //val intent = Intent(this, InstructionsFragment::class.java)
            //startActivity(intent)
            // NavHostFragment.findNavController(R.id.nav_graph)

            //findNavController(this,R.id.nav_host_fragment)//.navigate(R.id.goto_instructions_from_main)
       // }


    }

    suspend fun tryOBDInterfaceConnection() {
        var context = this

        withContext(Dispatchers.IO) {
            var obdConn : OBDConnector = OBDConnector(context)

            if(obdConn.verify()){
                Log.i("OBDCONNECTOR", "OBD INTERFACE FOUND!")
                // goto dashboard
                Log.i("OBDCONNECTOR", "GO TO DASHBOARD")

                //obdConn.execOBDCommand(SpeedCommand())

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






/*  val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
StrictMode.setThreadPolicy(policy);


//client.outputStream.write("Hello from the client!".toByteArray())

Log.i("OBDCONNECTOR", "clsed")

}
}

override fun onCreateOptionsMenu(menu: Menu): Boolean {
// Inflate the menu; this adds items to the action bar if it is present.
menuInflater.inflate(R.menu.menu_main, menu)
return true
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
// Handle action bar item clicks here. The action bar will
// automatically handle clicks on the Home/Up button, so long
// as you specify a parent activity in AndroidManifest.xml.
return when (item.itemId) {
R.id.action_settings -> true
else -> super.onOptionsItemSelected(item)
}
}

*/