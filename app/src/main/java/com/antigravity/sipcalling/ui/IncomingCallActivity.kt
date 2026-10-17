package com.antigravity.sipcalling.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.antigravity.sipcalling.core.SipManager
import com.antigravity.sipcalling.databinding.ActivityIncomingCallBinding
import com.antigravity.sipcalling.model.VoipCallState
import com.antigravity.sipcalling.util.SoundManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreen()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "SIP Caller"
        val callerUri = intent.getStringExtra("CALLER_URI") ?: "sip:unknown"

        binding.tvCallerName.text = callerName
        binding.tvCallerUri.text = callerUri

        SoundManager.startRingtoneAndVibration(this)

        setupButtons()
        observeCallTermination()
    }

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun setupButtons() {
        binding.btnAccept.setOnClickListener {
            SoundManager.stopRingtoneAndVibration(this)
            val accepted = SipManager.acceptCall()
            if (accepted) {
                val callIntent = Intent(this, CallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(callIntent)
            }
            finish()
        }

        binding.btnDecline.setOnClickListener {
            SoundManager.stopRingtoneAndVibration(this)
            SipManager.terminateCall()
            finish()
        }
    }

    private fun observeCallTermination() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SipManager.activeCallData.collectLatest { callData ->
                    if (callData == null || callData.state == VoipCallState.Terminated) {
                        SoundManager.stopRingtoneAndVibration(this@IncomingCallActivity)
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SoundManager.stopRingtoneAndVibration(this)
        super.onDestroy()
    }
}
