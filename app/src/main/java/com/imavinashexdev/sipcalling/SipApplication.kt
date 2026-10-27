package com.imavinashexdev.sipcalling

import android.app.Application
import android.util.Log
import com.imavinashexdev.sipcalling.core.SipManager
import com.imavinashexdev.sipcalling.data.CallLogRepository
import com.imavinashexdev.sipcalling.data.ContactRepository

class SipApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing SIP Calling Application")
        SipManager.initialize(this)
        CallLogRepository.initialize(this)
        ContactRepository.initialize(this)
    }

    companion object {
        private const val TAG = "SipApplication"
    }
}
