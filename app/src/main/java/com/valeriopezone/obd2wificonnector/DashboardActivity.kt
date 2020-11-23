package com.valeriopezone.obd2wificonnector

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.at.AdapterVoltageCommand
import com.github.eltonvs.obd.command.at.DescribeProtocolNumberCommand
import com.github.eltonvs.obd.command.at.IgnitionMonitorCommand
import com.github.eltonvs.obd.command.control.ModuleVoltageCommand
import com.github.eltonvs.obd.command.control.PendingTroubleCodesCommand
import com.github.eltonvs.obd.command.control.PermanentTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.command.fuel.FuelLevelCommand
import com.github.eltonvs.obd.command.fuel.FuelTypeCommand
import com.github.eltonvs.obd.command.pressure.FuelPressureCommand
import com.github.eltonvs.obd.command.temperature.AirIntakeTemperatureCommand
import com.github.eltonvs.obd.command.temperature.AmbientAirTemperatureCommand
import com.github.eltonvs.obd.command.temperature.EngineCoolantTemperatureCommand
import com.github.eltonvs.obd.command.temperature.OilTemperatureCommand
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.GridLabelRenderer
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import java.text.NumberFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class DashboardActivity : AppCompatActivity(), CoroutineScope {

    private val pointsPerGraph:Int = 50

    private var repeatedCommands : List<ObdCommand> = listOf(
            EngineCoolantTemperatureCommand(),
            SpeedCommand(),
            RPMCommand(),
            OilTemperatureCommand(),
            AdapterVoltageCommand(),
            IgnitionMonitorCommand(),
            FuelPressureCommand(),
            FuelLevelCommand(),
            ModuleVoltageCommand(),
            AirIntakeTemperatureCommand(),
            AmbientAirTemperatureCommand()
    )
/*,
            */
    private var staticCommands : List<ObdCommand> = listOf(
            DescribeProtocolNumberCommand(),
            FuelTypeCommand(),
            TroubleCodesCommand(),
            PendingTroubleCodesCommand(),
            PermanentTroubleCodesCommand()

)

    private var obdLastDatapoints : MutableMap<String, Array<DataPoint>> = mutableMapOf()
    //private var obdLastResponseValues : MutableMap<String, String> = mutableMapOf()

    private var mapGraphIdsByCMD : Map<String,Int> = mapOf(
            Pair(EngineCoolantTemperatureCommand().name,R.id.graph_realtime_coolant_temperature),
            Pair(SpeedCommand().name,R.id.graph_realtime_speed),
            Pair(RPMCommand().name,R.id.graph_realtime_rpm),
            Pair(OilTemperatureCommand().name,R.id.graph_oil_temperature)
    )
    private var textIndicatorIdsByCMD : Map<String,Int> = mapOf(
            Pair(DescribeProtocolNumberCommand().name,R.id.monitor_protocol),
            Pair(ModuleVoltageCommand().name,R.id.monitor_voltage),
            Pair(AdapterVoltageCommand().name,R.id.monitor_adapter_voltage),
            Pair(FuelTypeCommand().name,R.id.monitor_fuel_type),
            Pair(IgnitionMonitorCommand().name,R.id.monitor_ignition),
            Pair(FuelPressureCommand().name,R.id.monitor_fuel_pressure),
            Pair(FuelLevelCommand().name,R.id.monitor_fuel_level),
            Pair(AirIntakeTemperatureCommand().name,R.id.monitor_air_intake),
            Pair(AmbientAirTemperatureCommand().name,R.id.monitor_ambient_air),
            Pair(TroubleCodesCommand().name,R.id.monitor_trouble_codes),
            Pair(PendingTroubleCodesCommand().name,R.id.monitor_pending_codes),
            Pair(PermanentTroubleCodesCommand().name,R.id.monitor_permanent_codes)
    )

    private var seriesGraphs: MutableMap<String,LineGraphSeries<DataPoint>> = mutableMapOf()//arrayOf(LineGraphSeries(arrayOf(DataPoint(0.0000, 0.0000))))

    private var staticListObtained:Boolean = false
    private val nFormat: NumberFormat = NumberFormat.getInstance(Locale.FRANCE)
    private var mHandler=  Handler()
    private var mainOBDTimer : Runnable? = null


    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val obd_wifi_url = prefs.getString("obd_wifi_url","192.168.0.10").toString()
        val obd_wifi_port = Integer.parseInt(prefs.getString("obd_wifi_port","35000"))

        findViewById<CheckedTextView>(R.id.monitor_wifi_adapter).text = obd_wifi_url + ":" + obd_wifi_port
        loadPageData()



    }

    fun loadPageData(){
        //initialize datapoints
        repeatedCommands.forEach {
            Log.i("OBD DASHBOARD ACTIVITY", "SETTING CMD " + it.rawCommand.toString())

            obdLastDatapoints.set(it.name,Array(pointsPerGraph) { DataPoint(0.0,0.0) })
            seriesGraphs.set(it.name,LineGraphSeries(Array(pointsPerGraph) { DataPoint(0.0,0.0) }))

            for(i in 0..pointsPerGraph-1) {
                obdLastDatapoints[it.name]?.set(i,DataPoint(i.toDouble(),0.0))
            }

        }

        //initialize graphs
        seriesGraphs[SpeedCommand().name]?.let { setGraph(R.id.graph_realtime_speed, "-", it, 0.0, 180.0) }
        seriesGraphs[RPMCommand().name]?.let { setGraph(R.id.graph_realtime_rpm, "-", it, 0.0, 4500.0)}
        seriesGraphs[OilTemperatureCommand().name]?.let { setGraph(R.id.graph_oil_temperature, "-", it, 0.0, 250.0)}
        seriesGraphs[EngineCoolantTemperatureCommand().name]?.let { setGraph(R.id.graph_realtime_coolant_temperature, "-", it, -30.0, 150.0)}

    }

    fun setGraph(gview: Int, label: String, dataSeries: LineGraphSeries<DataPoint>, minY:Double, maxY:Double){
        //mGraph.getGridLabelRenderer().setHumanRounding(false);
        val speed_graph = findViewById<GraphView>(gview)
        speed_graph.setTitle(label);
        speed_graph.addSeries(dataSeries)
        speed_graph.getViewport().setMinY(minY)
        speed_graph.getViewport().setMaxY(maxY)
        speed_graph.getViewport().setMinX(0.0)
        speed_graph.getViewport().setMaxX(pointsPerGraph.toDouble())
        speed_graph.getViewport().setYAxisBoundsManual(true)
        speed_graph.getViewport().setXAxisBoundsManual(true)
        speed_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false)
        speed_graph.getGridLabelRenderer().setGridStyle( GridLabelRenderer.GridStyle.HORIZONTAL)
    }

    fun updateGraphLabel(gview:Int, label:String){
        val speed_graph = findViewById<GraphView>(gview)
        speed_graph.setTitle(label);
    }


    override fun onResume(){
        super.onResume()
        staticListObtained = false
        loadPageData()

        mainOBDTimer = object : Runnable {
            override fun run() {
                val context = this
                CoroutineScope(Main).launch{

                    val job = async {
                        var obdLocalConn : OBDConnector = OBDConnector(this@DashboardActivity)
                        if(!obdLocalConn.connect()){
                            findViewById<TextView>(R.id.odb_connection_status).text = "Disconnected :("
                            findViewById<TextView>(R.id.odb_connection_status).setTextColor(Color.parseColor("#FF5722"))
                            mHandler.postDelayed(context, 50)
                        }else {
                            findViewById<TextView>(R.id.odb_connection_status).text = "Connected!"
                            findViewById<TextView>(R.id.odb_connection_status).setTextColor(Color.parseColor("#4CAF50"))
                            val commandsToSend = if (staticListObtained) {
                                repeatedCommands
                            } else {
                                staticCommands
                            }

                            commandsToSend.forEach {
                                suspend {
                                    //val obdResponseText: String = async { requestData(it, obdLocalConn) }.await()
                                    val obdResponseText: String = async {obdLocalConn.execOBDCommand(it)}.await()


                                    //obdLastResponseValues[it.name] = obdResponseText
                                    if(obdResponseText != "") {
                                        launch {

                                            val gId = mapGraphIdsByCMD[it.name]
                                            if (gId != null) {
                                                manageDatapoints(it, obdResponseText)
                                                manageGraphData(gId, it, "")
                                            }
                                            val tId = textIndicatorIdsByCMD[it.name]
                                            if (tId != null) {
                                                manageTextIndicator(tId, obdResponseText)
                                            }
                                        }
                                    }
                                    delay(10)
                                }.invoke()

                            }
                        }

                        staticListObtained = true
                        obdLocalConn.close()

                    }.await()
                    Log.i("OBDCONNECTOR", "delay")
                    mHandler.postDelayed(context, 50)
                    //}
                }
            }
        }

        mHandler.postDelayed(mainOBDTimer as Runnable, 300)
    }


    override fun onPause() {
        mHandler.removeCallbacks(mainOBDTimer!!)
        super.onPause()
    }

    fun manageGraphData(gview : Int, cmd: ObdCommand, label : String){
        Log.i("OBDDATAPOINT", "UPDATE GRAPH")
        val gData  = obdLastDatapoints[cmd.name]
        if(gData != null){
            updateGraphLabel(gview, obdLastDatapoints.get(cmd.name)?.last()?.y.toString() + cmd.defaultUnit)
            seriesGraphs[cmd.name]?.resetData(gData)
        }
    }

    fun manageTextIndicator(view : Int, label: String){
        findViewById<CheckedTextView>(view).text = label
    }

   /* suspend fun requestData(cmd: ObdCommand, conn : OBDConnector):String {
        var context = this
        val response = async {conn?.execOBDCommand(cmd)}.await()
        var res:String = "0"
        if(response != null){
            res = response
        }
        return res
    }*/

    private fun manageDatapoints(cmd : ObdCommand, lastResponse : String) : Unit{
        var Value : Double = 0.0

        try{
            val number: Number = nFormat.parse(lastResponse)
            Value = number.toDouble()
            //manage datapoint
        }catch (t: Throwable){
            //use 0
        }

        //insert at top of datapoints
        val currentDataPoints = obdLastDatapoints[cmd.name]
        if (currentDataPoints != null) {
            /*if(!firstValueAlreadyObtained[cmd.name]!!){
                for(i in 0..pointsPerGraph-2) {
                    //obdLastDatapoints[cmd.name]?.set(i,DataPoint(i.toDouble(), currentDataPoints[i+1].y))
                    obdLastDatapoints[cmd.name]?.set(i,DataPoint(i.toDouble(), Value))
                }
                firstValueAlreadyObtained[cmd.name] = true
            //}else{*/
                for(i in 0..pointsPerGraph-2) {
                    obdLastDatapoints[cmd.name]?.set(i,DataPoint(i.toDouble(), currentDataPoints[i+1].y))
                }
            //}

            obdLastDatapoints[cmd.name]?.set(pointsPerGraph-1,DataPoint((pointsPerGraph-1).toDouble(), Value))

            for(i in 0..pointsPerGraph-1) {
                Log.i("OBDDATAPOINT", "aft  " + cmd.name + " to " + obdLastDatapoints[cmd.name]?.get(i).toString())
            }
            Log.i("OBDDATAPOINT", "-")

        }
    }
}





/*
    obdConn  = OBDConnector(this)

        GlobalScope.launch{
            obdConn!!.connect()
        }

        Log.i("OBDRESPONSEVALSINIT", "launch")
        CoroutineScope(Main).launch{
            val job = async {
                initGraphCommands.forEach {
                    suspend {
                        val obdResponseText: String = async { requestData(it) }.await()
                        obdLastResponseValues[it.name] = obdResponseText
                         Log.i("OBDRESPONSEVALSINIT", "GET FROM  " + it + " -> " + obdResponseText)
                        delay(15)
                    }.invoke()
                }
            }.await()
            Log.i("OBDRESPONSEVALSINIT", "FINISH1")

            findViewById<CheckedTextView>(R.id.monitor_protocol).text = "Protocol : " + obdLastResponseValues[DescribeProtocolNumberCommand().name]
            findViewById<CheckedTextView>(R.id.monitor_ignition).text = "Ignition : " + obdLastResponseValues[IgnitionMonitorCommand().name]
            findViewById<CheckedTextView>(R.id.monitor_voltage).text = "Voltage : " + obdLastResponseValues[AdapterVoltageCommand().name]
        }
        Log.i("OBDRESPONSEVALSINIT", "FINISH2")




        */
/*
        mTimer2 = object : Runnable {
            override fun run() {

                    Log.i("OBDDATAPOINT", "UPDATE GRAPH")

                val speedData  = obdLastDatapoints[SpeedCommand().name]
                if(speedData != null){
                    updateGraphLabel(R.id.graph_realtime_speed, "Speed : " + obdLastDatapoints.get(SpeedCommand().name)?.last()?.y.toString() + SpeedCommand().defaultUnit)
                    //series1.resetData(speedData)
                    seriesGraphs[SpeedCommand().name]?.resetData(speedData)
                }

                val rpmData  = obdLastDatapoints[RPMCommand().name]
                if(rpmData != null){
                    updateGraphLabel(R.id.graph_realtime_rpm, "°" + obdLastDatapoints.get(RPMCommand().name)?.last()?.y.toString() + RPMCommand().defaultUnit)
                    series2.resetData(obdLastDatapoints.get(RPMCommand().name))
                }

                val oilTemperatureData  = obdLastDatapoints[OilTemperatureCommand().name]
                if(oilTemperatureData != null){
                    updateGraphLabel(R.id.graph_oil_temperature, "°" + obdLastDatapoints.get(OilTemperatureCommand().name)?.last()?.y.toString() + OilTemperatureCommand().defaultUnit)
                    series3.resetData(obdLastDatapoints.get(OilTemperatureCommand().name))
                }

                val coolantData  = obdLastDatapoints[EngineCoolantTemperatureCommand().name]
                if(coolantData != null){
                    updateGraphLabel(R.id.graph_realtime_coolant_temperature, "°" + obdLastDatapoints.get(EngineCoolantTemperatureCommand().name)?.last()?.y.toString() + EngineCoolantTemperatureCommand().defaultUnit)
                    series4.resetData(obdLastDatapoints.get(EngineCoolantTemperatureCommand().name))
                }

                mHandler.postDelayed(this, 1000)
            }
        }*/

//        obdResponseValues?.set(cmd, arrayOf(DataPoint(0.0000, res)))
/*

   private fun generateData(): Array<DataPoint?>? {

//        return arrayOf(obdResponseValues.get(SpeedCommand()))
        val count = 30
        val values = arrayOfNulls<DataPoint>(count)
        for (i in 0 until count) {
            val x = i.toDouble()
            val f: Double = mRand.nextDouble() * 0.15 + 0.3
            val y: Double = Math.sin(i * f + 2) + mRand.nextDouble() * 0.3
            val v = DataPoint(x, y)
            values[i] = v
        }
        return values
    }

    var mLastRandom = 2.0
    var mRand: Random = Random()
    private fun getRandom(): Double {
        return mRand.nextDouble() * 0.5 - 0.25.let { mLastRandom += it; mLastRandom }
    }



 val context = this
                CoroutineScope(Main).launch{
                    //runBlocking {
                        graphCommands.forEach {
                            //CoroutineScope(Main).launch{///remove if panic

                            val obdResponseText: String = async { requestData(it) }.await()
                            obdLastResponseValues[it] = obdResponseText
                            manageDatapoints(it, obdResponseText)
                           // Log.i("OBDRESPONSEVALS", "GET FROM  " + it + " -> " + obdResponseText)
                            delay(250)
                            //Thread.sleep(200)


                        }
                    mHandler.postDelayed(context, 200)
                    //}
                }

 */
