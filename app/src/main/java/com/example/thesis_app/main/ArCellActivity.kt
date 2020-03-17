package com.example.thesis_app.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import kotlin.math.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.example.thesis_app.helper.AugmentedRealityLocationUtils.INITIAL_MARKER_SCALE_MODIFIER
import com.example.thesis_app.helper.AugmentedRealityLocationUtils.INVALID_MARKER_SCALE_MODIFIER
import com.example.thesis_app.R
import com.example.thesis_app.databinding.ArCellActivityBinding
import com.example.thesis_app.helper.*
import com.example.thesis_app.viewModel.ArCellActivityViewModel
import com.google.android.gms.location.*
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ModelRenderable
import uk.co.appoly.arcorelocation.LocationMarker
import uk.co.appoly.arcorelocation.LocationScene
import java.util.concurrent.CompletableFuture
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.android.synthetic.main.location_layout_renderable.view.*

class ArCellActivity : AppCompatActivity(), SensorEventListener {

    private val tag = "testDinos"
    //-- my variables --
    //ar core needed variables
    private var arCoreInstallRequested = false
    //these values are used for ArCore calls
    private lateinit var myArFragment: ArFragment
    private lateinit var myArSceneView: ArSceneView
    // Our ARCore-Location scene
    private var locationScene: LocationScene? = null
    private var arHandler = Handler(Looper.getMainLooper())
    //runnable for updates
    private val resumeArElementsTask = Runnable {
        locationScene?.resume()
        myArSceneView.resume()
    }
    //set of towers information
    private var towersSet: MutableSet<Cell> = mutableSetOf()
    private var areAllMarkersLoaded = false
    //the cell we are going to track (we track only the primary cell)
    private lateinit var servingCell: Cell
    private lateinit var telephonyManager: TelephonyManager

    //the coordinates of the cell in Vector3 format relevant to our location
    private var cellToVector3: Vector3 = Vector3(0f, 0f, 0f)

    // location specific variables
    private var locationManager: LocationManager? = null
    private val userLocation = Location(LocationManager.NETWORK_PROVIDER)
    private var firstLocationBoolean = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    //sensors are needed to find true north
    private lateinit var sensorManager: SensorManager
    // Magnetic rotational data
    private var accelerometerReading: FloatArray? = FloatArray(3)
    private var magnetometerReading: FloatArray? = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    // azimuth for heading
    private var azimuth: Float = 0f
    //data binding
    private lateinit var binding: ArCellActivityBinding
    //view model
    private lateinit var viewModel: ArCellActivityViewModel

    private val customListener = object : PhoneStateListener() {

        @SuppressLint("MissingPermission")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            Log.d(tag, "signals changed")

            if (!PermissionUtils.hasLocationAndPhonePermissions(this@ArCellActivity)) {
                PermissionUtils.requestPhoneAndLocationPermissions(this@ArCellActivity)
            } else {
                val cellInfo = telephonyManager.allCellInfo
                for (cell in cellInfo) {
                    if (cell.isRegistered)
                        when (cell) {
                            is CellInfoLte -> {
                                if (cell.cellIdentity.ci.toString() == servingCell.id) {
                                    changeSignalStrength(cell.cellSignalStrength.dbm)
                                }
                            }
                            is CellInfoWcdma -> {
                                if (cell.cellIdentity.cid.toString() == servingCell.id) {
                                    changeSignalStrength(cell.cellSignalStrength.dbm)
                                }
                            }
                            is CellInfoCdma -> {
                                if (cell.cellIdentity.basestationId.toString() == servingCell.id) {
                                    changeSignalStrength(cell.cellSignalStrength.cdmaDbm)
                                }
                            }
                            is CellInfoGsm -> {
                                if (cell.cellIdentity.cid.toString() == servingCell.id) {
                                    changeSignalStrength(cell.cellSignalStrength.dbm)
                                }
                            }
                        }
                }
            }

            super.onSignalStrengthsChanged(signalStrength)
        }
    }

    //-- my methods --
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.ar_cell_activity)
        binding = DataBindingUtil.setContentView(this, R.layout.ar_cell_activity)
        //get the view model
        viewModel = ViewModelProviders.of(this).get(ArCellActivityViewModel::class.java)
        // Set the viewmodel for databinding - this allows the bound layout access to all of the
        // data in the VieWModel
        binding.arCellViewModel = viewModel
        // Specify the current activity as the lifecycle owner of the binding. This is used so that
        // the binding can observe LiveData updates
        binding.lifecycleOwner = this

        //-- instantiate main elements --
        //sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        //telephony manager for signal strength
        telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//        val mPhoneStateListener = CustomPhoneStateListener()
        telephonyManager.listen(customListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        //location services
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        if (!locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }

        //arcore elements settings
        myArFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment
        myArSceneView = myArFragment.arSceneView
        //disable the instruction hand in screen
        myArFragment.planeDiscoveryController.hide()
        myArFragment.planeDiscoveryController.setInstructionView(null)
        //we disable the plane renderer because we don't want this functionality
        myArSceneView.planeRenderer.isEnabled = false

        //after onCreate the onResume function is called so we have to set up our variables till then
        setupLocationData()
    }

    fun changeSignalStrength(power: Int) {
        binding.frameSignalStrength.text = getString(R.string.cell_signal_power, power)
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
        mLocationRequest = LocationRequest()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        myArSceneView.session?.let {
            locationScene?.pause()
            myArSceneView.pause()
        }
        stopLocationUpdates()
    }

    private fun setupLocationData() {
        Log.d(tag, "setupActivity")
        //first we clear all the variables
        towersSet.clear()
        //get activity extras
        val cell = intent.extras!!.get("cell0") as Cell
        towersSet.add(cell)
        //we set the first cell as the default (for now)
        servingCell = cell
        binding.frameCellid.text = getString(R.string.textview_cellid, cell.id)
        binding.frameTypeOfService.text = getString(R.string.type_of_service, cell.type_of_service)
        binding.frameSignalStrength.text = getString(R.string.cell_signal_power, cell.signal_strength)
        //for second sim card
        if (intent.hasExtra("cell1")) {
            val cell1 = intent.extras!!.get("cell1") as Cell
            towersSet.add(cell1)
        }
        //for third sim card == never!
        if (intent.hasExtra("cell2")) {
            val cell2 = intent.extras!!.get("cell2") as Cell
            towersSet.add(cell2)
        }

    }

    private fun buildAlertMessageNoGps() {

        val builder = AlertDialog.Builder(this)
        builder.setMessage("Your GPS seems to be disabled, you must enable it for the app to run smoothly")
            .setCancelable(false)
            .setPositiveButton("Go to settings") { _, _ ->
                startActivityForResult(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    , 11
                )
            }
        val alert: AlertDialog = builder.create()
        alert.show()


    }

    //we need these operation functions for the above calculations
    operator fun Vector3.plus(other: Vector3): Vector3 {
        return Vector3.add(this, other)
    }

    operator fun Vector3.minus(other: Vector3): Vector3 {
        return Vector3.subtract(this, other)
    }

    private fun checkAndRequestPermissions() {
        Log.d(tag, "checkAndRequestPermissions")
        if (!PermissionUtils.hasLocationAndCameraPermissions(this)) {
            PermissionUtils.requestCameraAndLocationPermissions(this)
        } else {
            setupSession()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        Log.d(tag, "onRequestPermissionsResult")
        if (!PermissionUtils.hasLocationAndCameraPermissions(this)) {
            Toast.makeText(this, R.string.camera_and_location_permission_request, Toast.LENGTH_LONG)
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

        if (myArSceneView.session == null) {
            try {
                val session =
                    AugmentedRealityLocationUtils.setupSession(this, arCoreInstallRequested)
                if (session == null) {
                    arCoreInstallRequested = true
                    return
                } else {
                    myArSceneView.setupSession(session)
                }
            } catch (e: UnavailableException) {
                AugmentedRealityLocationUtils.handleSessionException(this, e)
            }
        }

        if (locationScene == null) {
            locationScene = LocationScene(this, myArSceneView)
//            locationScene!!.setMinimalRefreshing(true)
//            locationScene!!.setOffsetOverlapping(true)
//            locationScene!!.setRemoveOverlapping(true)
            locationScene!!.anchorRefreshInterval = 500
        }

        try {
            resumeArElementsTask.run()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "Unable to get camera", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        //create tower pins
        renderTowers()
        //create 3d models at the antennas site (on 0 height)
        createModels()
    }

    fun renderTowers() {
        Log.d(tag, "renderTowers")
        //clear the markers if any
        areAllMarkersLoaded = false
        locationScene!!.clearMarkers()

        setupAndRenderTowersMarkers()
        updateTowersMarkers()
    }

    private fun createModels() {

        //create a tower model for each Cell we have found in CellActivity
        for (cell in towersSet) {
            //first we find the distance and bearing to cells
            val specificCellLocation = Location(LocationManager.NETWORK_PROVIDER)
            specificCellLocation.latitude = cell.latitude
            specificCellLocation.longitude = cell.longitude
            val distanceToCell = userLocation.distanceTo(specificCellLocation)
            val bearingToCell = userLocation.bearingTo(specificCellLocation)

            //create the Vector3 from the distance and bearing
            val x = distanceToCell * cos(bearingToCell)
            val y = distanceToCell * sin(bearingToCell)
            cellToVector3 = Vector3(x, y, 0f)

            ModelRenderable.builder()
                .setSource(this, Uri.parse("radio_tower.sfb"))
                .build()
                .thenAccept { modelRenderable ->
                    //position
                    val cameraForward: Vector3 = myArSceneView.scene.camera.forward
                    //rotation
                    val cameraPosition: Vector3 = myArSceneView.scene.camera.worldPosition
                    //pose
                    val position = cameraPosition + cameraForward
                    val direction = cameraPosition - position
                    direction.y = position.y

                    Node().apply {
                        worldPosition = position//3d model position
                        renderable = modelRenderable//set the tower as node.renderable
                        setParent(myArSceneView.scene)
                        setLookDirection(direction)//0,0,0
                        localPosition =
                            cellToVector3 //set the relevant position of the tower as we calculated from the distance and bearing
                    }
                }
        }

    }

    private fun setupAndRenderTowersMarkers() {
        Log.d(tag, "setupAndRenderTowersMarkers")

        towersSet.forEach { cell ->
            val completableFutureViewRenderable = ViewRenderable.builder()
                .setView(this, R.layout.location_layout_renderable)
                .build()

            CompletableFuture.anyOf(completableFutureViewRenderable)
                .handle<Any> { _, throwable ->
                    //here we know the renderable was built or not
                    if (throwable != null) {
                        // handle renderable load fail
                        DemoUtils.displayError(this, "Unable to load renderables", throwable)
                        return@handle null
                    }
                    try {
                        val towerMarker = LocationMarker(
                            cell.longitude, cell.latitude,
                            setTowerNode(cell, completableFutureViewRenderable)
                        )

                        arHandler.postDelayed({
                            //attach the view markers
                            attachMarkerToScene(towerMarker, completableFutureViewRenderable.get().view)
                            arHandler.post {
                                locationScene?.refreshAnchors()
                            }
                            areAllMarkersLoaded = true
                        }, 200)

                    } catch (ex: Exception) {
                        // showToast(getString(R.string.generic_error_msg))
                    }
                    null
                }
        }
    }

    private fun updateTowersMarkers() {
        Log.d(tag, "updateTowersMarkers")
        myArSceneView.scene.addOnUpdateListener()
        {
            if (!areAllMarkersLoaded) {
                return@addOnUpdateListener
            }

            locationScene?.mLocationMarkers?.forEach { locationMarker ->
                locationMarker.height =
                    AugmentedRealityLocationUtils.generateRandomHeightBasedOnDistance(
                        locationMarker?.anchorNode?.distance ?: 0
                    )
            }

            val frame = myArSceneView.arFrame ?: return@addOnUpdateListener
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return@addOnUpdateListener
            }
            locationScene!!.processFrame(frame)
        }
    }

    private fun setTowerNode(cell: Cell, completableFuture: CompletableFuture<ViewRenderable>): Node {
        Log.d(tag, "setTowerNode")
        val node = Node()
        node.renderable = completableFuture.get()

        val nodeLayout = completableFuture.get().view
        val towerName = nodeLayout.name
        val markerLayoutContainer = nodeLayout.pinContainer
        towerName.text = getString(R.string.textview_cellid, cell.id)
        markerLayoutContainer.visibility = View.GONE
        nodeLayout.setOnTouchListener { _, _ ->
            Toast.makeText(this, "Follow the pin to get to the cell.", Toast.LENGTH_LONG).show()
            servingCell = cell
            binding.frameCellid.text = getString(R.string.textview_cellid, cell.id)
            binding.frameTypeOfService.text = getString(R.string.type_of_service, cell.type_of_service)
            false
        }
        return node
    }

    private fun attachMarkerToScene(locationMarker: LocationMarker, layoutRendarable: View) {
        Log.d(tag, "attachMarkerToScene")
        resumeArElementsTask.run {
            locationMarker.scalingMode = LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN
            locationMarker.scaleModifier = INITIAL_MARKER_SCALE_MODIFIER

            //we attach markers on anchors
            locationScene?.mLocationMarkers?.add(locationMarker)
            locationMarker.anchorNode?.isEnabled = true

            arHandler.post {
                locationScene?.refreshAnchors()
                layoutRendarable.pinContainer.visibility = View.VISIBLE
            }
        }
        locationMarker.setRenderEvent { locationNode ->
            layoutRendarable.distance.text =
                AugmentedRealityLocationUtils.showDistance(locationNode.distance)
            resumeArElementsTask.run {
                computeNewScaleModifierBasedOnDistance(locationMarker, locationNode.distance)
            }
        }
//        bearingCalcuationRunnable.run()
    }

    private fun computeNewScaleModifierBasedOnDistance(locationMarker: LocationMarker, distance: Int) {
        //Log.d(tag, "computeNewScaleModifierBasedOnDistance")
        val scaleModifier =
            AugmentedRealityLocationUtils.getScaleModifierBasedOnRealDistance(distance)
        return if (scaleModifier == INVALID_MARKER_SCALE_MODIFIER) {
            detachMarker(locationMarker)
        } else {
            locationMarker.scaleModifier = scaleModifier
        }
    }

    private fun detachMarker(locationMarker: LocationMarker) {
        Log.d(tag, "detachMarker")
        locationMarker.anchorNode?.anchor?.detach()
        locationMarker.anchorNode?.isEnabled = false
        locationMarker.anchorNode = null
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    private fun startLocationUpdates() {

        // Create the location request to start receiving updates
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 2000
        mLocationRequest.fastestInterval = 1000

        // Create LocationSettingsRequest object using location request
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        val locationSettingsRequest = builder.build()

        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        if (!PermissionUtils.hasCoarseAndFineLocationPermissions(this)) {
            PermissionUtils.requestCoarseAndFineLocationPermissions(this)
        }
        fusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // do work here
            locationResult.lastLocation
            onLocationChanged(locationResult.lastLocation)
        }
    }

    fun onLocationChanged(location: Location) {
        // New location has now been determined

        if (firstLocationBoolean) {
            userLocation.latitude = location.latitude
            userLocation.longitude = location.longitude
            firstLocationBoolean = false
        }

        val usableCellLocation = Location(LocationManager.NETWORK_PROVIDER)
        usableCellLocation.latitude = servingCell.latitude
        usableCellLocation.longitude = servingCell.longitude
        //set new distance to cell
        binding.frameDistancetocell.text =
            getString(R.string.textview_distancetocell, location.distanceTo(usableCellLocation))
        //have true north as relevance
        binding.frameBearingTn.text = getString(R.string.bearing_to_cell, azimuth)
        val newBearing = location.bearingTo(usableCellLocation)
        //set new bearing to cell
        binding.frameBearing.text = getString(R.string.bearing_to_true_north, azimuth - newBearing)
    }

    // Get readings from accelerometer and magnetometer.
    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        //Sensor change value
        when (sensorEvent!!.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerReading = sensorEvent.values.clone()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerReading = sensorEvent.values.clone()
            }
        }

        if (magnetometerReading != null && accelerometerReading != null) {
            // Rotation matrix based on current readings from accelerometer and magnetometer.
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            azimuth = orientationAngles[0] * 360 / (2 * 3.14159f)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        //this functions just needs to exist because mainactivity extends sensorlistener
    }


}
