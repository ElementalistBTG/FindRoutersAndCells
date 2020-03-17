package com.example.thesis_app.helper

import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class CloudAnchorFragment : ArFragment() {

    private val tag2 = "testDinos"

    override fun getSessionConfiguration(session: Session?): Config {
        Log.d(tag2, "called config")
        planeDiscoveryController.setInstructionView(null)
        val config: Config = super.getSessionConfiguration(session)
        config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
        return config
    }
}