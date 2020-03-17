package com.example.thesis_app.helper

import android.app.Activity
import android.widget.Toast
import com.example.thesis_app.R
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*

object AugmentedRealityLocationUtils {

    private const val RENDER_MARKER_MIN_DISTANCE = 1//meters
    private const val RENDER_MARKER_MAX_DISTANCE = 2000//meters
    const val INVALID_MARKER_SCALE_MODIFIER = -1F
    const val INITIAL_MARKER_SCALE_MODIFIER = 0.5f

    @Throws(UnavailableException::class)
    fun setupSession(activity: Activity, installRequested: Boolean): Session? {
        when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                return null
            }
            ArCoreApk.InstallStatus.INSTALLED -> {
                //just continue with session setup
            }
            else -> {
                //just continue with session setup
            }
        }

        val session = Session(activity)
        val config = Config(session)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        // IMPORTANT!!!  ArSceneView requires the `LATEST_CAMERA_IMAGE` non-blocking update mode.

        session.configure(config)
        return session
    }

    fun handleSessionException(activity: Activity, sessionException: UnavailableException) {
        val message = when (sessionException) {
            is UnavailableArcoreNotInstalledException ->
                activity.resources.getString(R.string.arcore_not_installed)

            is UnavailableUserDeclinedInstallationException ->
                activity.resources.getString(R.string.arcore_not_installed)

            is UnavailableApkTooOldException ->
                activity.resources.getString(R.string.arcore_not_updated)

            is UnavailableSdkTooOldException ->
                activity.resources.getString(R.string.arcore_not_supported)

            else -> activity.resources.getString(R.string.arcore_not_supported)
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    fun getScaleModifierBasedOnRealDistance(distance: Int): Float {
        return when (distance) {
            in Integer.MIN_VALUE..RENDER_MARKER_MIN_DISTANCE -> INVALID_MARKER_SCALE_MODIFIER
            in RENDER_MARKER_MIN_DISTANCE + 1..20 -> 0.8f
            in 21..40 -> 0.75f
            in 41..60 -> 0.7f
            in 61..80 -> 0.65f
            in 81..100 -> 0.6f
            in 101..500 -> 0.5f
            in 501..800 -> 0.45f
            in 801..1800 -> 0.4f
            in 1801..RENDER_MARKER_MAX_DISTANCE -> 0.35f
            in RENDER_MARKER_MAX_DISTANCE + 1..Integer.MAX_VALUE -> 0.15f
            else -> -1f
        }
    }

    fun generateRandomHeightBasedOnDistance(distance: Int): Float {
        return when (distance) {
            in 0..5 -> 1f
            in 6..200 -> (1..3).random().toFloat()
            in 201..600 -> (4..6).random().toFloat()
            in 601..1000 -> (7..9).random().toFloat()
            in 1001..1500 -> (10..12).random().toFloat()
            in 1501..RENDER_MARKER_MAX_DISTANCE -> (12..13).random().toFloat()
            else -> 0f
        }
    }

    fun showDistance(distance: Int): String {
        return if (distance >= 1000)
            String.format("%.2f", (distance.toDouble() / 1000)) + " km"
        else
            "$distance m"
    }
}
