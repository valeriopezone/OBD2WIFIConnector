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
import com.github.eltonvs.obd.command.*
import com.github.eltonvs.obd.command.at.*
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

    //INIT CMD SEQUENCE
    private var staticCommands : List<ObdCommand> = listOf(
            ResetAdapterCommand(),
            SetEchoCommand(Switcher.OFF),
            SetEchoCommand(Switcher.OFF),
            CustomOBDCommand("M0"),
            SetLineFeedCommand(Switcher.OFF),
            SetSpacesCommand(Switcher.OFF),
            CustomOBDCommand("@1"),
            CustomOBDCommand("I"),
            SetHeadersCommand(Switcher.OFF),
            SetAdaptiveTimingCommand(AdaptiveTimingMode.AUTO_1),
            DescribeProtocolNumberCommand(),
            FuelTypeCommand(),
            TroubleCodesCommand(),
            PendingTroubleCodesCommand(),
            PermanentTroubleCodesCommand()

    )

    //POOL CMD SEQUENCE
    private var repeatedCommands : List<ObdCommand> = listOf(
         /*   ResetAdapterCommand(),
            SetEchoCommand(Switcher.OFF),*/
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


    private var obdLastDatapoints : MutableMap<String, Array<DataPoint>> = mutableMapOf()

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

    override fun onResume(){
        super.onResume()
        staticListObtained = false
        loadPageData()

        mainOBDTimer = object : Runnable {
            override fun run() {
                val context = this
                CoroutineScope(Main).launch{

                    val job = async {
                        val obdLocalConn : OBDConnector = OBDConnector(this@DashboardActivity)
                        if(!obdLocalConn.connect()){
                            findViewById<TextView>(R.id.odb_connection_status).text = "Disconnected :("
                            findViewById<TextView>(R.id.odb_connection_status).setTextColor(Color.parseColor("#FF5722"))
                            mHandler.postDelayed(context, 50)
                        }else{
                            findViewById<TextView>(R.id.odb_connection_status).text = "Connected!"
                            findViewById<TextView>(R.id.odb_connection_status).setTextColor(Color.parseColor("#4CAF50"))
                            val commandsToSend = if (staticListObtained) {
                                repeatedCommands
                            } else {
                                staticCommands
                            }

                                commandsToSend.forEach {//loop through all commands to send
                                    suspend {
                                        val obdRes: ObdResponse = async {obdLocalConn.execOBDCommand(it)}.await()
                                        if (obdRes.value != "") {
                                            launch {
                                                val gId = mapGraphIdsByCMD[it.name]//get graph xml element
                                                if (gId != null) {
                                                    manageDatapoints(it, obdRes.value)//shift datapoints
                                                    manageGraphData(gId, it, obdRes.formattedValue)//update graph value
                                                }
                                                val tId = textIndicatorIdsByCMD[it.name]//get text indicator xml element
                                                if (tId != null) {
                                                    manageTextIndicator(tId, obdRes.formattedValue)//update indicator value
                                                }
                                            }
                                        }
                                        delay(10)//wait before next request
                                    }.invoke()

                                }
                        }

                        staticListObtained = true
                        obdLocalConn.close()

                    }.await()

                    mHandler.postDelayed(context, 10)//launch next loop

                    //}
                }
            }
        }


        //launch timer
        mHandler.postDelayed(mainOBDTimer as Runnable, 50)
    }


    override fun onPause() {
        mHandler.removeCallbacks(mainOBDTimer!!)
        super.onPause()
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





    fun manageGraphData(gview : Int, cmd: ObdCommand, label : String){
        val gData  = obdLastDatapoints[cmd.name]
        if(gData != null){
            updateGraphLabel(gview, label)//obdLastDatapoints.get(cmd.name)?.last()?.y.toString() + cmd.defaultUnit
            seriesGraphs[cmd.name]?.resetData(gData)
        }
    }

    fun manageTextIndicator(view : Int, label: String){
        findViewById<CheckedTextView>(view).text = label
    }


    private fun manageDatapoints(cmd : ObdCommand, lastResponse : String) : Unit{
        var Value : Double = 0.0

        try{
            val number: Number = nFormat.parse(lastResponse)
            Value = number.toDouble()
            //manage datapoint

            //shift every datapoint to left and insert new value in last position
            val currentDataPoints = obdLastDatapoints[cmd.name]
            if (currentDataPoints != null) {

                for(i in 0..pointsPerGraph-2) {
                    obdLastDatapoints[cmd.name]?.set(i,DataPoint(i.toDouble(), currentDataPoints[i+1].y))
                }
                obdLastDatapoints[cmd.name]?.set(pointsPerGraph-1,DataPoint((pointsPerGraph-1).toDouble(), Value))


            }

            
        }catch (t: Throwable){
            //use 0
        }


    }
}




