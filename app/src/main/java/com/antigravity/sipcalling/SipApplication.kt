package com.antigravity.sipcalling

import android.app.Application
import android.util.Log
import com.antigravity.sipcalling.core.SipManager

class SipApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing SIP Calling Application")
        SipManager.initialize(this)
    }

    companion object {
        private const val TAG = "SipApplication"
    }
}
