package com.lekowatch.guardian

import android.app.Application
import com.google.firebase.FirebaseApp

class LekoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
