package fr.ziyon.campzone

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CampzoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Firebase.firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(
                persistentCacheSettings {
                    setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                }
            )
        }
    }
}
