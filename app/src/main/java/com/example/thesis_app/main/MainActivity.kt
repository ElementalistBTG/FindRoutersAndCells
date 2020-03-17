package com.example.thesis_app.main

import android.content.Intent
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.thesis_app.R
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : AppCompatActivity() {

    //Declare Global Variables
    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        // Initialisation phase of app

        cellButton.setOnClickListener {
            // Handler code here.
            val intent = Intent(applicationContext, CellActivity::class.java)
            startActivity(intent)
        }

        wifiButton.setOnClickListener {
            // Handler code here.
            val intent = Intent(applicationContext, wifiActivity::class.java)
            startActivity(intent)
        }
    }
}
