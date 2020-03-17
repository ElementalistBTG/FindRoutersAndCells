package com.example.thesis_app.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.telephony.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.thesis_app.helper.Cell
import com.example.thesis_app.R
import org.json.JSONObject
import com.example.thesis_app.databinding.ActivityCellBinding
import com.example.thesis_app.helper.PermissionUtils
import kotlinx.android.synthetic.main.activity_cell.*
import java.io.Serializable

class CellActivity : AppCompatActivity() {

    private val tag = "testDinos"
    //managers
    private lateinit var telephonyManager: TelephonyManager
    //execute function once when all data are gathered
    private var runFunctionOnce = false
    //cell id to be passed to next activity
    private var towersSet: MutableSet<Cell> = mutableSetOf()
    private val testHandler: Handler = Handler()

    private lateinit var binding: ActivityCellBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_cell)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_cell)
        //binding.lifecycleOwner = this

        telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        // -- find cells info --
        //first request permissions and then retrieve data
        scanCellular(telephonyManager)
        testHandler.postDelayed(
            {
                binding.progressBar1.visibility = View.GONE
                binding.cellSignalsView.text = "Analyzing cell signals complete"
            },1000)
        testHandler.postDelayed(
            {
                binding.progressBar2.visibility = View.GONE
                binding.myLocationView.text = "User location found"
            },1800)
        testHandler.postDelayed(
            {
                binding.progressBar3.visibility = View.GONE
                binding.cellLocationView.text = "Cell location found"
            },2400)
        testHandler.postDelayed(
            {
                binding.progressBar4.visibility = View.GONE
                binding.distanceView.text = "Distance to cell calculated"
            },3500)
    }

    override fun onResume() {
        super.onResume()
        checkDataRunnable.run()
    }

    override fun onPause() {
        super.onPause()
        testHandler.removeCallbacks(checkDataRunnable)
    }

    private fun scanCellular(TM: TelephonyManager) {

        if (!PermissionUtils.hasLocationAndPhonePermissions(this)) {
            PermissionUtils.requestPhoneAndLocationPermissions(this)
        } else {
            //permissions already granted
            getTelephoneStats(TM)
        }
        return
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PermissionUtils.PHONE_AND_LOCATION_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted
                    getTelephoneStats(telephonyManager)
                } else {
                    // permission denied
                    Toast.makeText(
                        applicationContext,
                        "3G results are not available when Location or phone services are not given permission",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getTelephoneStats(TM: TelephonyManager) {
        val cellInfo = TM.allCellInfo
        if (cellInfo != null && cellInfo.isNotEmpty()) {
            for (cell in cellInfo) {
                when (cell) {
                    //create http request to https://opencellid.org with cell info
                    //the values change for each type of service
                    is CellInfoGsm -> {
                        requestCoordinates(
                            cell.cellIdentity.mccString,
                            cell.cellIdentity.mncString,
                            cell.cellIdentity.lac.toString(),
                            cell.cellIdentity.cid.toString(),
                            cell.cellSignalStrength.dbm,
                            cell.isRegistered,
                            "GSM"
                        )
                    }
                    is CellInfoCdma -> {
                        //here we can get latitude and longitude right away
                        val newCell = Cell(
                            cell.cellIdentity.basestationId.toString(),
                            convertToDouble(cell.cellIdentity.latitude),
                            convertToDouble(cell.cellIdentity.longitude),
                            cell.isRegistered,
                            cell.cellSignalStrength.cdmaDbm,
                            "CDMA"
                        )
                        towersSet.add(newCell)
                    }
                    is CellInfoWcdma -> {
                        requestCoordinates(
                            cell.cellIdentity.mccString,
                            cell.cellIdentity.mncString,
                            cell.cellIdentity.lac.toString(),
                            cell.cellIdentity.cid.toString(),
                            cell.cellSignalStrength.dbm,
                            cell.isRegistered,
                            "WCDMA"
                        )

                    }
                    is CellInfoLte -> {
                        requestCoordinates(
                            cell.cellIdentity.mccString,
                            cell.cellIdentity.mncString,
                            cell.cellIdentity.tac.toString(),
                            cell.cellIdentity.ci.toString(),
                            cell.cellSignalStrength.dbm,
                            cell.isRegistered,
                            "LTE"
                        )
                    }
                }
            }
        }
    }

    private fun convertToDouble(coord: Int): Double {
        return (coord / 3600).toDouble()
    }

    private fun requestCoordinates(
        mcc: String?,
        mnc: String?,
        tac: String,
        id: String,
        signalStrength: Int,
        registered: Boolean,
        TOS: String
    ) {
        if(PermissionUtils.isNetworkConnected(this)){
            if (registered) {
                // Instantiate the RequestQueue and create the URL
                val url =
                    "https://opencellid.org/cell/get?key=269953e1b24658&mcc=$mcc&mnc=$mnc&lac=$tac&cellid=$id&format=json"
                Log.d(tag,url)
                val queue = Volley.newRequestQueue(this)
                // Request a string response from the provided URL.
                val stringReq =
                    JsonObjectRequest(Request.Method.GET, url, null, Response.Listener { response ->
                        val strResp = response.toString()
                        val jsonObj = JSONObject(strResp)
                        val returnLatitude = jsonObj.getString("lat")
                        val returnLongitude = jsonObj.getString("lon")
                        val newCell = Cell(
                            id,
                            returnLatitude.toDouble(),
                            returnLongitude.toDouble(),
                            registered,
                            signalStrength,
                            TOS
                        )
                        towersSet.add(newCell)
                        runFunctionOnce = true
                    },
                        Response.ErrorListener {
                            Toast.makeText(this, "Couldn't retrieve cell info", Toast.LENGTH_SHORT).show()
                        })
                queue.add(stringReq)
            }
        }else{
            AlertDialog.Builder(this).setTitle("No Internet Connection")
                .setMessage("Please check your internet connection and try again")
                .setPositiveButton(android.R.string.ok) { _, _ -> finish()}
                .setIcon(resources.getDrawable(android.R.drawable.ic_dialog_alert,null))
                .show()
        }
    }

    private val checkDataRunnable = object : Runnable {
        override fun run() {
            checkData()
            testHandler.postDelayed(this, 4000)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun checkData() {
        if (runFunctionOnce) {//first time it will check
            Log.d(tag,"run")
            runFunctionOnce = false
            val intent = Intent(applicationContext, ArCellActivity::class.java)
            var cell1 = ""
            for ((index,cell) in towersSet.withIndex()) {
                if(index==0 || cell1 != cell.id){
                    cell1 = cell.id
                    intent.putExtra("cell${index}",cell as Serializable)
                }
            }
            progressBar5.visibility = View.GONE
            binding.loadingARView.text = "AR loaded"
            //start ar activity
            startActivity(intent)
            testHandler.removeCallbacks(checkDataRunnable)
            finish()
        }
    }


}
