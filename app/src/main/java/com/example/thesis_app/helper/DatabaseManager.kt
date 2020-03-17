package com.example.thesis_app.helper

import android.util.Log
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DatabaseManager {

    var bssid : String? = ""
    var ssid : String? = ""
    private val tag = "testDinos"

    fun postRouter(cloudAnchorId: String){
        val databaseReference = FirebaseDatabase.getInstance().reference.child("router_dir")

        val postListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get Post object and use the values to update the UI
                val post = dataSnapshot.getValue(Router::class.java)
                post?.let{
                    ssid = it.ssid
                    bssid = it.bssid
                }
                Log.d(tag,"ssid $ssid  and bssid $bssid")

            }

            override fun onCancelled(databaseError: DatabaseError) {
                //we get error
                Log.d("testDinos","error -----------------")
            }
        }
        databaseReference.addListenerForSingleValueEvent(postListener)
    }

}