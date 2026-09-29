package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class AnonApplication : Application() {
    companion object {
        lateinit var instance: AnonApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initFirebaseSafely()
    }

    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    // 1차: google-services.json을 통한 자동 초기화 시도
                    FirebaseApp.initializeApp(this)
                    Log.d("AnonApplication", "FirebaseApp initialized via google-services.json")
                } catch (e: Exception) {
                    // 2차: OAuth / firebase-applet-config 실제 프로젝트 옵션으로 초기화
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:768097478635:android:71212dbb7e992e04ab9bae")
                        .setApiKey("AIzaSyBpFN-x9_2jJatKOINxVW0f5g8dyMqt5pk")
                        .setProjectId("anon-3299a")
                        .setGcmSenderId("768097478635")
                        .setStorageBucket("anon-3299a.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    Log.d("AnonApplication", "FirebaseApp initialized with verified FirebaseOptions")
                }
            }
        } catch (e: Exception) {
            Log.w("AnonApplication", "FirebaseApp init warning: ${e.message}")
        }
    }
}
