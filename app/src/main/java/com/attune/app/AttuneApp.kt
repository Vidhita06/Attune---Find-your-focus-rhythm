package com.attune.app
import android.app.Application
import com.attune.app.data.KeystoreSenderHasher
import com.attune.prediction.decision.SenderKey
import dagger.hilt.android.HiltAndroidApp
@HiltAndroidApp
class AttuneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SenderKey.install(KeystoreSenderHasher()) // before anything computes a sender id; the key itself is created lazily
    }
}
