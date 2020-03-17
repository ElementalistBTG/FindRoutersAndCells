package com.example.thesis_app.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import com.example.thesis_app.R
import com.example.thesis_app.helper.PermissionUtils
import kotlinx.android.synthetic.main.activity_wifi.*

class wifiActivity : AppCompatActivity() {

    private val tag = "testDinos"

    //wifi variables
    lateinit var wifiManager: WifiManager
    var wifiResultList = ArrayList<ScanResult>()
    private lateinit var wifiListView: ListView
    var wifiArrayList = ArrayList<String>()
    var wifiBSSIDArrayList = ArrayList<String>()
    var wifiSSIDArrayList = ArrayList<String>()
    var wifiPowerArrayList = ArrayList<Int>()
    var wifiFrequencyArrayList = ArrayList<Int>()
    private lateinit var adapterForWifiList: ArrayAdapter<String> // adapter for wifi ListView
    //handler
    private var myHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (!wifiManager.isWifiEnabled) {
            AlertDialog.Builder(this).setTitle("Wifi required")
                .setMessage("Please enable wifi first to have this app work")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(intent)
                }
                .setIcon(resources.getDrawable(android.R.drawable.ic_dialog_alert, null))
                .show()
                .setCancelable(false)
        }
        if(!PermissionUtils.hasFineLocationPermission(this)){
            PermissionUtils.requestFineLocationPermission(this)
        }

        wifiListView = findViewById(R.id.wifiList)
        adapterForWifiList = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, wifiArrayList)
        wifiListView.adapter = adapterForWifiList

        wifiListView.setOnItemClickListener { _, _, pos, _ ->
            //Log.d(tag, "got the item: ${wifiBSSIDArrayList[pos]}")
            val wifiConnectedInfo : WifiInfo = wifiManager.connectionInfo
            var connectedAPssid = wifiConnectedInfo.ssid
            connectedAPssid = connectedAPssid.replace(("\\p{P}").toRegex(), "")
             if(wifiSSIDArrayList[pos]!=connectedAPssid){
                 Log.d(tag, "got the items: ${wifiSSIDArrayList[pos]} and ${wifiConnectedInfo.ssid}")
                 val intentToWifiNetworks = Intent(WifiManager.ACTION_PICK_WIFI_NETWORK)
                 startActivity(intentToWifiNetworks)
             }else{
                 val intent = Intent(applicationContext, ArWifiActivity::class.java).apply {
                     putExtra("SSID", wifiSSIDArrayList[pos])
                     putExtra("BSSID", wifiBSSIDArrayList[pos])
                     putExtra("Power", wifiPowerArrayList[pos])
                     putExtra("frequency", wifiFrequencyArrayList[pos])
                 }
                 startActivity(intent)
             }
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wifiResultList = wifiManager.scanResults as ArrayList<ScanResult>
            applicationContext.unregisterReceiver(this)
            wifiArrayList.clear()
            wifiFrequencyArrayList.clear()
            wifiSSIDArrayList.clear()
            wifiBSSIDArrayList.clear()
            wifiPowerArrayList.clear()
            //Log.d(tag, "new wifi readings")
            for (result in wifiResultList) {
                wifiArrayList.add("SSID: " + result.SSID + " / Power: " + result.level + "db at " + result.frequency + "Hz")
                wifiFrequencyArrayList.add(result.frequency)
                wifiSSIDArrayList.add(result.SSID)
                wifiBSSIDArrayList.add(result.BSSID)
                wifiPowerArrayList.add(result.level)
            }
            adapterForWifiList.notifyDataSetChanged()
            wifiInstructions.visibility = View.VISIBLE
            // Log.d(tag, wifiArrayList.toString())

        }
    }

    @Suppress("DEPRECATION")
    private fun scanWifi() {
        applicationContext.registerReceiver(
            wifiReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        if (!PermissionUtils.hasFineLocationPermission(this)) {
            // Permission is not granted
            PermissionUtils.requestFineLocationPermission(this)
        } else {
            val wifiInfo: WifiInfo = wifiManager.connectionInfo
            if (wifiInfo.networkId != -1) {
                val ssid = wifiInfo.ssid
                connectedWifiNetwork.text = getString(R.string.connected_wifi_textview, ssid)
                connectedWifiNetwork.visibility = View.VISIBLE
            }
            wifiManager.startScan()
            Toast.makeText(applicationContext, "Scanning WiFi ...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PermissionUtils.FINE_LOCATION_PERMISSION_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the fine-location-related task you need to do.
                    scanWifi()
                } else {
                    // permission denied, boo! Disable the functionality that depends on this permission.
                    Toast.makeText(
                        applicationContext,
                        "WiFi results are not available when Location is not given permission",
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

    private val scanWifiPeriodically = object : Runnable {
        override fun run() {
            scanWifi()
            // Run code again after 10 seconds
            myHandler.postDelayed(this, 10000)
        }
    }

    override fun onResume() {
        super.onResume()
        //Get wi-fi readings periodically
        scanWifiPeriodically.run()
    }

    override fun onPause() {
        super.onPause()
        myHandler.removeCallbacks(scanWifiPeriodically)
    }

}
