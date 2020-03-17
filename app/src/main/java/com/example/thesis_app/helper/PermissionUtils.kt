package com.example.thesis_app.helper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService

object PermissionUtils {

    const val CAMERA_PERMISSION_CODE = 0
    const val FINE_LOCATION_PERMISSION_CODE = 1
    const val CAMERA_AND_LOCATION_PERMISSION_CODE = 3
    const val PHONE_AND_LOCATION_PERMISSION_CODE = 4
    const val COARSE_AND_FINE_LOCATION_PERMISSION_CODE = 5

    private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
    private val permissionsCameraLocation =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    private val permissionsPhoneLocation =
        arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION)
    private val permissionsFineAndCoarseLocation =
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)


    fun hasLocationAndCameraPermissions(activity: Activity): Boolean {

        permissionsCameraLocation.forEach { permission ->
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun requestCameraAndLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            permissionsCameraLocation,
            CAMERA_AND_LOCATION_PERMISSION_CODE
        )
    }

    fun hasCameraPermission(activity: Activity): Boolean {
        return (ActivityCompat.checkSelfPermission(
            activity,
            CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }

    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(CAMERA_PERMISSION),
            CAMERA_PERMISSION_CODE
        )
    }

    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            CAMERA_PERMISSION
        )
    }

    fun launchPermissionSettings(activity: Activity) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    fun hasLocationAndPhonePermissions(activity: Activity): Boolean {

        permissionsPhoneLocation.forEach { permission ->
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun requestPhoneAndLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            permissionsPhoneLocation,
            PHONE_AND_LOCATION_PERMISSION_CODE
        )
    }

    fun hasCoarseAndFineLocationPermissions(activity: Activity): Boolean {

        permissionsFineAndCoarseLocation.forEach { permission ->
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun requestCoarseAndFineLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            permissionsFineAndCoarseLocation,
            COARSE_AND_FINE_LOCATION_PERMISSION_CODE
        )
    }

    fun hasFineLocationPermission(activity: Activity): Boolean {
        return (ActivityCompat.checkSelfPermission(
            activity,
            LOCATION_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED)
    }

    fun requestFineLocationPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(LOCATION_PERMISSION),
            FINE_LOCATION_PERMISSION_CODE
        )
    }

    fun isNetworkConnected(activity: Activity): Boolean {

        val connectivityManager =
            activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager //Retrieves an instance of the ConnectivityManager class from the current application context.
        val networkInfo =
            connectivityManager.activeNetwork //Retrieves an instance of the NetworkInfo class that represents the current network connection. This will be null if no network is available.
        return networkInfo != null  //Check if there is an available network connection and the device is connected.
    }

}