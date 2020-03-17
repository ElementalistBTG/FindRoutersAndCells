package com.example.thesis_app.helper

import android.os.AsyncTask
import com.example.thesis_app.main.ArCellActivity
import uk.co.appoly.arcorelocation.LocationScene
import java.lang.ref.WeakReference

class LocationAsyncTask(private val activityWeakReference: WeakReference<ArCellActivity>) :
    AsyncTask<LocationScene, Void, List<Double>>() {

    override fun doInBackground(vararg p0: LocationScene): List<Double> {
        var deviceLatitude: Double?
        var deviceLongitude: Double?
        do {
            deviceLatitude = p0[0].deviceLocation?.currentBestLocation?.latitude
            deviceLongitude = p0[0].deviceLocation?.currentBestLocation?.longitude
        } while (deviceLatitude == null || deviceLongitude == null)
        return listOf(deviceLatitude, deviceLongitude)
    }

    override fun onPostExecute(result: List<Double>?) {
        super.onPostExecute(result)
        activityWeakReference.get()!!.renderTowers()
    }
}