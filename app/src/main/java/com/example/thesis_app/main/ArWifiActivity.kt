package com.example.thesis_app.main

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.example.thesis_app.R
import com.example.thesis_app.databinding.ArWifiActivityBinding
import com.example.thesis_app.helper.*
import com.example.thesis_app.viewModel.ArWifiActivityViewModel
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.ar_wifi_activity.*
import kotlinx.android.synthetic.main.location_layout_renderable.view.*
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.math.*

class ArWifiActivity : AppCompatActivity() {

    private val tag = "testDinos"
    lateinit var wifiManager: WifiManager
    //-- my variables --
    //ar core needed variables
    private var arCoreInstallRequested = false
    //these values are used for ArCore calls
    private lateinit var myArFragment: CloudAnchorFragment
    private lateinit var myArSceneView: ArSceneView
    //runnable for updates
    private val resumeArElementsTask = Runnable {
        myArSceneView.resume()
    }
    //anchors
    private val anchors: ArrayList<Anchor> = arrayListOf()
    //default frequency
    private var frequency = 2400
    //data binding
    private lateinit var binding: ArWifiActivityBinding
    //view model
    private lateinit var viewModel: ArWifiActivityViewModel

    private var ssid: String? = ""
    var bssid: String? = ""

    //distance readings array
//    private val anchorPointArray: ArrayList<AnchorPoint> = arrayListOf()
//    private var distanceCounter = 0
    private val distanceArray: ArrayList<Double> = arrayListOf()

    //for coroutines
    private val myJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + myJob)

    //for cloud anchors
    private val cloudAnchorManager = CloudAnchorManager()
    private var firebaseManager: FirebaseManager? = null
    private var anchorPendingUpload: Anchor? = null
    private var counterShortCode = 1
    //anchors
    private val myCloudAnchors: ArrayList<Anchor?> = arrayListOf()
    private var cloudAnchorNode: AnchorNode? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.ar_wifi_activity)
        binding = DataBindingUtil.setContentView(this, R.layout.ar_wifi_activity)
        //get the view model
        viewModel = ViewModelProviders.of(this).get(ArWifiActivityViewModel::class.java)
        // Set the viewmodel for databinding - this allows the bound layout access to all of the
        // data in the VieWModel
        binding.arWifiViewModel = viewModel
        // Specify the current activity as the lifecycle owner of the binding. This is used so that
        // the binding can observe LiveData updates
        binding.lifecycleOwner = this

        //setup the screen
        ssid = intent.getStringExtra("SSID")
        Log.d(tag, "got a ssid: " + ssid!!)
        bssid = intent.getStringExtra("BSSID")
        Log.d(tag, "got a bssid: " + bssid!!)
        val power = intent.getIntExtra("Power", 0)
        Log.d(tag, "with power: $power")
        frequency = intent.getIntExtra("frequency", 0)
        Log.d(tag, "with frequency: $frequency")
        binding.wifiBssid.append(bssid)
        binding.wifiSignalStrength.text = getString(R.string.current_signal_strength_textview, power.toFloat())

        myArFragment = supportFragmentManager.findFragmentById(R.id.wifi_ar_fragment) as CloudAnchorFragment
        myArFragment.arSceneView.scene.addOnUpdateListener { frameTime -> cloudAnchorManager.onUpdate() }
        //disable the instruction hand in screen and the plane controller
        myArFragment.planeDiscoveryController.hide()
        myArFragment.planeDiscoveryController.setInstructionView(null)

        myArSceneView = myArFragment.arSceneView
        myArSceneView.planeRenderer.isEnabled = false

        firebaseManager = FirebaseManager(applicationContext)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "You need to enable the wifi for the app to run", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "We will be tracking the AP with BSSID: $bssid of the $ssid network",
                Toast.LENGTH_LONG
            ).show()
        }

        var counter = 0
        var takeReadings = 0f

        //buttons
        capture_button.setOnClickListener {
            anchorPlacement()

            uiScope.launch(Dispatchers.Main) {
                takeReadings = take3wifiReadings(wifiManager)
                binding.wifiSignalStrength.text = getString(R.string.current_signal_strength_textview, takeReadings)
                calculateDistance(takeReadings)
                if (counter >= 2) {
                    val x1 = anchors[counter - 2].pose.translation[0]
                    val y1 = anchors[counter - 2].pose.translation[1]
                    val x2 = anchors[counter - 1].pose.translation[0]
                    val y2 = anchors[counter - 1].pose.translation[1]
                    val x3 = anchors[counter].pose.translation[0]
                    val y3 = anchors[counter].pose.translation[1]
                    calculateThreeCircleIntersection(
                        x1,
                        y1,
                        distanceArray[counter - 2],
                        x2,
                        y2,
                        distanceArray[counter - 1],
                        x3,
                        y3,
                        distanceArray[counter]
                    )
                }
                //increase counter after every click
                counter++
            }
        }

        clear_button.setOnClickListener { v -> onClearButtonPressed() }
        clear_button.isEnabled = false

        resolve_button.setOnClickListener { v -> onResolveButtonPressed() }

        upload_button.setOnClickListener { v -> onUploadButtonPressed() }
        upload_button.isEnabled = false

        place_router_button.setOnClickListener { v -> onPlaceRouterButtonPressed() }
    }

    @Synchronized
    private fun onPlaceRouterButtonPressed() {
        if (myArFragment.arSceneView.arFrame!!.camera.trackingState == TrackingState.TRACKING) {
            val cameraPose =
                myArFragment.arSceneView.arFrame!!.camera.displayOrientedPose.compose(
                    Pose.makeTranslation(0f, 0f, -0.2f)
                )
            // If an AnchorNode existed before, remove and nullify it.
            if (cloudAnchorNode != null) {
                myArSceneView.scene.removeChild(cloudAnchorNode!!)
                cloudAnchorNode = null
            }
            //create the new anchor
            val anchor = myArFragment.arSceneView.session!!.createAnchor(cameraPose)
            setNewAnchor(anchor)
            anchorPendingUpload = anchor
            Log.d(tag, "Anchor pending is: $anchorPendingUpload")
            Toast.makeText(this, "Press Upload to host router to the cloud", Toast.LENGTH_SHORT).show()
            resolve_button.isEnabled = false
            upload_button.isEnabled = true
            clear_button.isEnabled = true
        } else {
            Log.d(tag, "post delayed called")
            Toast.makeText(this, "Wait because the camera is not yet ready", Toast.LENGTH_SHORT).show()
            //camera not ready yet
            Handler().postDelayed({
                onPlaceRouterButtonPressed()
            }, 1000)
        }
    }

    @Synchronized
    private fun onClearButtonPressed() {
        Log.d(tag, "onClearButtonPressed")
        // Clear the anchor from the scene.
        cloudAnchorManager.clearListeners()

        resolve_button.isEnabled = true
        upload_button.isEnabled = false
        clear_button.isEnabled = false
        cloudAnchorNode = null
        eraseAnchors()
        Toast.makeText(this, "All routers shown erased", Toast.LENGTH_SHORT).show()
        counterShortCode = 1 //reset value
    }

    private fun eraseAnchors() {
        //Log.d("testing",myCloudAnchors.toString())
        for (anchor in myCloudAnchors) {
            anchor?.detach()
        }
        myCloudAnchors.clear()
    }

    // Modify the renderables when a new anchor is available.
    @Synchronized
    private fun setNewAnchor(anchor: Anchor?) {
        Log.d(tag, "setNewAnchor")
        myCloudAnchors.add(anchor)
        // Create the Anchor Node
        cloudAnchorNode = AnchorNode(anchor)
        myArSceneView.scene!!.addChild(cloudAnchorNode!!)

        ModelRenderable.builder()
            .setSource(this, Uri.parse("gloworb.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                // TransformableNode means the user to move, scale and rotate the model
                val transformableNode = TransformableNode(myArFragment.transformationSystem)
                transformableNode.renderable = modelRenderable
                transformableNode.setParent(cloudAnchorNode)
                myArFragment.arSceneView.scene.addChild(cloudAnchorNode)
                transformableNode.select()
            }

        //create the pin
//        ViewRenderable.builder()
//            .setView(this, R.layout.location_layout_renderable)
//            .build()
//            .thenAccept { viewRenderable ->
//                val transformableNode = TransformableNode(myArFragment.transformationSystem)
//                transformableNode.renderable = viewRenderable
//                transformableNode.localPosition = Vector3(0f,0.1f,-0.1f)//little up and further
//                transformableNode.scaleController.maxScale = 0.3f
//                transformableNode.scaleController.minScale = 0.29f
//                val imageView = viewRenderable.view
//                val networkName = imageView.name
//                val networkDistance = imageView.distance
//                networkName.text = bssid
//                networkDistance.text = "5"
//
//                transformableNode.setParent(cloudAnchorNode)
//                myArFragment.arSceneView.scene.addChild(cloudAnchorNode)
//            }
    }




private fun onUploadButtonPressed() {
    Log.d(tag, "Now hosting anchor")

//        val router = Router(ssid,bssid)
//        Log.d(tag,router.toString())
//        firebaseDatabase.child("router").setValue(router)
//        val valueOfRouter = firebaseDatabase.child("router")
//        Log.d(tag, "router: $valueOfRouter")

    cloudAnchorManager.hostCloudAnchor(
        myArSceneView.session!!, anchorPendingUpload
    ) { this.onHostedAnchorAvailable(it) }
    anchorPendingUpload = null
    Toast.makeText(this, "Router is being hosted to the cloud", Toast.LENGTH_SHORT).show()
}

@Synchronized
private fun onHostedAnchorAvailable(anchor: Anchor) {
    Log.d(tag, "onHostedAnchorAvailable")
    val cloudState = anchor.cloudAnchorState
    if (cloudState == CloudAnchorState.SUCCESS) {
        val cloudAnchorId = anchor.cloudAnchorId

//            val databaseManager = DatabaseManager()
//            databaseManager.postRouter(cloudAnchorId)

        firebaseManager!!.nextShortCode { shortCode ->
            if (shortCode != null) {
                firebaseManager!!.storeUsingShortCode(shortCode, cloudAnchorId)
                Toast.makeText(this, "Cloud Anchor Hosted. Short code: $shortCode", Toast.LENGTH_SHORT).show()
            } else {
                // Firebase could not provide a short code.
                Toast.makeText(
                    this,
                    "Cloud Anchor Hosted, but could not get a short code from Firebase.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    } else {
        Toast.makeText(this, "Error while hosting: $cloudState", Toast.LENGTH_SHORT).show()
    }
}

@Synchronized
private fun onResolveButtonPressed() {
    resolve_button.isEnabled = false
    clear_button.isEnabled = true
    materializeAnchors()
}

private fun materializeAnchors() {
    firebaseManager!!.getCloudAnchorId(counterShortCode) { cloudAnchorId ->
        Log.d(tag, "Cloud anchor id: $cloudAnchorId")
        if (cloudAnchorId != null && cloudAnchorId.isNotEmpty() && cloudAnchorId != "null") {
            Toast.makeText(this, "Visualising existing routers in the cloud", Toast.LENGTH_SHORT).show()
            cloudAnchorManager.resolveCloudAnchor(myArSceneView.session!!, cloudAnchorId)
            { anchor -> onResolvedAnchorAvailable(anchor, counterShortCode) }
        } else if (counterShortCode == 1) {
            resolve_button.isEnabled = true
            Toast.makeText(this, "No cloud anchors to load", Toast.LENGTH_SHORT).show()
            return@getCloudAnchorId
        } else {
            resolve_button.isEnabled = true
            return@getCloudAnchorId
        }
    }
}

@Synchronized
private fun onResolvedAnchorAvailable(anchor: Anchor, shortCode: Int) {
    val cloudState = anchor.cloudAnchorState
    if (cloudState == CloudAnchorState.SUCCESS) {
        Toast.makeText(this, "Cloud Anchor Resolved. Short code: $shortCode", Toast.LENGTH_SHORT).show()
        setNewAnchor(anchor)
        counterShortCode++
        materializeAnchors()
    } else {
//            Toast.makeText(
//                this, "Error while resolving anchor with short code "
//                        + shortCode
//                        + ". Error: "
//                        + cloudState.toString(), Toast.LENGTH_SHORT
//            ).show()
        //when we pass through all anchors
        resolve_button!!.isEnabled = false
    }
}

private fun calculateDistance(signalStrength: Float) {
    val exponent = (27.55 - (20 * log(frequency.toFloat(), 10f)) - signalStrength) / 20
    var distance = 10.toDouble().pow(exponent)
    //round to 3 decimal numbers
    distance = (distance * 1000).roundToInt().toDouble() / 1000
    Log.d(tag, "distance is $distance")
    Toast.makeText(this, "distance is  $distance", Toast.LENGTH_SHORT).show()
    binding.wifiDistance.text = getString(R.string.distance_wifi_textview_new, distance)
    distanceArray.add(distance)
}

private fun calculateThreeCircleIntersection(
    x1: Float, y1: Float, r1: Double,
    x2: Float, y2: Float, r2: Double,
    x3: Float, y3: Float, r3: Double
) {
    //find the common intersection of the first two circles
    //and then see which point fits the third circle
    val y =
        ((x2 - x3) * ((x2 * x2 - x1 * x1) + (y2 * y2 - y1 * y1) + (r1 * r1 - r2 * r2)) - (x1 - x2) * ((x3 * x3 - x2 * x2) + (y3 * y3 - y2 * y2) + (r2 * r2 - r3 * r3))) / 2 * ((y1 - y2) * (x2 - x3) - (y2 - y3) * (x1 - x2))
    val x =
        ((y2 - y3) * ((y2 * y2 - y1 * y1) + (x2 * x2 - x1 * x1) + (r1 * r1 - r2 * r2)) - (y1 - y2) * ((y3 * y3 - y2 * y2) + (x3 * x3 - x2 * x2) + (r2 * r2 - r3 * r3))) / 2 * ((x1 - x2) * (y2 - y3) - (x2 - x3) * (y1 - y2))

    val bearing = atan2(y - y3, x - x3)
    val myDegrees = Math.toDegrees(bearing)
    binding.wifiBearing.text = getString(R.string.bearing_wifi_textview_new, "%.3f".format(myDegrees))

}

//capture an anchor
private fun anchorPlacement() {
    if (myArFragment.arSceneView.arFrame!!.camera.trackingState == TrackingState.TRACKING) {
        val cameraPose =
            myArFragment.arSceneView.arFrame!!.camera.displayOrientedPose.compose(
                Pose.makeTranslation(
                    0f,
                    0f,
                    0f
                )
            )
        val anchor = myArFragment.arSceneView.session!!.createAnchor(cameraPose)
        val anchorNode = AnchorNode(anchor)
        anchors.add(anchor)
        //you can just comment these lines as it
        //is not necessary to place something on the
        //screen for the app to work
        ModelRenderable.builder()
            .setSource(this, Uri.parse("Glow_Stick.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                // TransformableNode means the user to move, scale and rotate the model
                val transformableNode = TransformableNode(myArFragment.transformationSystem)
                transformableNode.renderable = modelRenderable
                transformableNode.setParent(anchorNode)
                myArFragment.arSceneView.scene.addChild(anchorNode)
                transformableNode.select()
            }
    } else {
        Log.d(tag, "post delayed called")
        Toast.makeText(this, "Wait because the camera is not yet ready", Toast.LENGTH_SHORT).show()
        //camera not ready yet
        Handler().postDelayed({
            anchorPlacement()
        }, 1000)
    }
}

//we can take measurements only for the active connection of the user
private suspend fun take3wifiReadings(wifiManager: WifiManager): Float {
    var sum = 0f
    Log.d(tag, "function called")
    for (i in 1..3) {
        sum += takeReading(wifiManager)
    }
    return (sum / 3)
}

private suspend fun takeReading(wifiManager: WifiManager): Int {
    val wifiInfo: WifiInfo = wifiManager.connectionInfo
    val rssi = wifiInfo.rssi
    Log.d(tag, "new reading $rssi")
    delay(500)
    return rssi
}

override fun onResume() {
    super.onResume()
    checkAndRequestPermissions()
}

override fun onPause() {
    super.onPause()
    myArSceneView.session?.let {
        myArSceneView.pause()
    }
}

private fun checkAndRequestPermissions() {
    Log.d(tag, "checkAndRequestPermissions")
    if (!PermissionUtils.hasCameraPermission(this)) {
        PermissionUtils.requestCameraPermission(this)
    } else {
        setupSession()
    }
}

override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
    Log.d(tag, "onRequestPermissionsResult")
    if (!PermissionUtils.hasCameraPermission(this)) {
        Toast.makeText(this, R.string.camera_permission_request, Toast.LENGTH_LONG)
            .show()
        if (!PermissionUtils.shouldShowRequestPermissionRationale(this)) {
            // Permission denied with checking "Do not ask again".
            PermissionUtils.launchPermissionSettings(this)
        }
        finish()
    } else {
        setupSession()
    }
}

private fun setupSession() {
    Log.d(tag, "setupSession")

    try {
        resumeArElementsTask.run()
    } catch (e: CameraNotAvailableException) {
        Toast.makeText(this, "Unable to get camera", Toast.LENGTH_LONG).show()
        finish()
        return
    }

}

}