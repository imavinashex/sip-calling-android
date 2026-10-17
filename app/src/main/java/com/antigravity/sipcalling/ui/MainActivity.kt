package com.antigravity.sipcalling.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.antigravity.sipcalling.R
import com.antigravity.sipcalling.core.SipManager
import com.antigravity.sipcalling.databinding.ActivityMainBinding
import com.antigravity.sipcalling.model.RegistrationStatus
import com.antigravity.sipcalling.model.VoipCallState
import com.antigravity.sipcalling.util.SoundManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted) {
            Toast.makeText(
                this,
                "Microphone permission is required to make and receive calls",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupDialpad()
        setupActions()
        observeRegistrationState()
        observeActiveCall()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupDialpad() {
        val setupKey = { root: View, digit: String, letters: String ->
            val tvDigit = root.findViewById<TextView>(R.id.tvDigit)
            val tvLetters = root.findViewById<TextView>(R.id.tvLetters)
            tvDigit.text = digit
            tvLetters.text = letters
            root.setOnClickListener {
                appendDigit(digit)
                SoundManager.playDtmfTone(digit.first())
            }
        }

        setupKey(binding.key1.root, "1", "")
        setupKey(binding.key2.root, "2", "ABC")
        setupKey(binding.key3.root, "3", "DEF")
        setupKey(binding.key4.root, "4", "GHI")
        setupKey(binding.key5.root, "5", "JKL")
        setupKey(binding.key6.root, "6", "MNO")
        setupKey(binding.key7.root, "7", "PQRS")
        setupKey(binding.key8.root, "8", "TUV")
        setupKey(binding.key9.root, "9", "WXYZ")
        setupKey(binding.keyStar.root, "*", "")
        setupKey(binding.key0.root, "0", "+")
        setupKey(binding.keyHash.root, "#", "")

        // Long press '0' for '+'
        binding.key0.root.setOnLongClickListener {
            appendDigit("+")
            true
        }

        // Backspace button
        binding.btnBackspace.setOnClickListener {
            val text = binding.etTargetNumber.text
            if (!text.isNullOrEmpty()) {
                binding.etTargetNumber.setText(text.subSequence(0, text.length - 1))
                binding.etTargetNumber.setSelection(binding.etTargetNumber.text?.length ?: 0)
            }
        }

        binding.btnBackspace.setOnLongClickListener {
            binding.etTargetNumber.setText("")
            true
        }
    }

    private fun appendDigit(digit: String) {
        val current = binding.etTargetNumber.text?.toString() ?: ""
        binding.etTargetNumber.setText(current + digit)
        binding.etTargetNumber.setSelection(binding.etTargetNumber.text?.length ?: 0)
    }

    private fun setupActions() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SipSettingsActivity::class.java))
        }

        binding.btnSetupAccount.setOnClickListener {
            startActivity(Intent(this, SipSettingsActivity::class.java))
        }

        binding.btnCall.setOnClickListener {
            initiateCall()
        }
    }

    private fun initiateCall() {
        val destination = binding.etTargetNumber.text?.toString()?.trim().orEmpty()
        if (destination.isEmpty()) {
            Toast.makeText(this, "Please enter a phone number or SIP address", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
            return
        }

        val success = SipManager.makeCall(destination)
        if (success) {
            val callIntent = Intent(this, CallActivity::class.java)
            startActivity(callIntent)
        } else {
            Toast.makeText(this, "Failed to initiate call. Check SIP account settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeRegistrationState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SipManager.registrationState.collectLatest { status ->
                    when (status) {
                        is RegistrationStatus.Registered -> {
                            binding.statusDot.backgroundTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(this@MainActivity, R.color.status_registered)
                            )
                            binding.tvStatus.text = "Online: ${status.identity}"
                            binding.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_registered))
                            binding.bannerConfigure.visibility = View.GONE
                        }

                        is RegistrationStatus.Registering -> {
                            binding.statusDot.backgroundTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(this@MainActivity, R.color.status_registering)
                            )
                            binding.tvStatus.text = getString(R.string.status_registering)
                            binding.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_registering))
                            binding.bannerConfigure.visibility = View.GONE
                        }

                        is RegistrationStatus.Failed -> {
                            binding.statusDot.backgroundTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(this@MainActivity, R.color.status_error)
                            )
                            binding.tvStatus.text = "Failed: ${status.reason}"
                            binding.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
                            binding.bannerConfigure.visibility = View.VISIBLE
                        }

                        is RegistrationStatus.Unregistered -> {
                            binding.statusDot.backgroundTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(this@MainActivity, R.color.status_offline)
                            )
                            binding.tvStatus.text = getString(R.string.status_offline)
                            binding.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                            binding.bannerConfigure.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun observeActiveCall() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SipManager.activeCallData.collectLatest { callData ->
                    if (callData != null && callData.state != VoipCallState.Terminated && callData.state != VoipCallState.IncomingReceived) {
                        // Open CallActivity if active call is in progress
                        val callIntent = Intent(this@MainActivity, CallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(callIntent)
                    }
                }
            }
        }
    }
}
