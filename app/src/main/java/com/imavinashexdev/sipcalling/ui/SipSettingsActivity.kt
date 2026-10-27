package com.imavinashexdev.sipcalling.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.imavinashexdev.sipcalling.R
import com.imavinashexdev.sipcalling.core.SipManager
import com.imavinashexdev.sipcalling.databinding.ActivitySipSettingsBinding
import com.imavinashexdev.sipcalling.model.SipAccount
import com.imavinashexdev.sipcalling.model.SipTransport

class SipSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySipSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySipSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadExistingSettings()
        setupListeners()
    }

    private fun loadExistingSettings() {
        val account = SipAccount.load(this) ?: return
        binding.etDomain.setText(account.domain)
        binding.etPort.setText(account.port.toString())
        binding.etUsername.setText(account.username)
        binding.etDisplayName.setText(account.displayName)
        binding.etPassword.setText(account.password)
        binding.etStun.setText(account.stunServer)

        when (account.transport) {
            SipTransport.UDP -> binding.rbUdp.isChecked = true
            SipTransport.TCP -> binding.rbTcp.isChecked = true
            SipTransport.TLS -> binding.rbTls.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnSaveAndConnect.setOnClickListener {
            saveAndConnect()
        }

        binding.btnDisconnect.setOnClickListener {
            disconnectAccount()
        }
    }

    private fun saveAndConnect() {
        val domain = binding.etDomain.text?.toString()?.trim().orEmpty()
        val portStr = binding.etPort.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val displayName = binding.etDisplayName.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val stunServer = binding.etStun.text?.toString()?.trim().orEmpty()

        if (domain.isEmpty()) {
            binding.tilDomain.error = "Domain / PBX host is required"
            return
        } else {
            binding.tilDomain.error = null
        }

        if (username.isEmpty()) {
            binding.tilUsername.error = "SIP Username / Extension is required"
            return
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "SIP Password is required"
            return
        } else {
            binding.tilPassword.error = null
        }

        val port = portStr.toIntOrNull() ?: 5060

        val transport = when (binding.rgTransport.checkedRadioButtonId) {
            R.id.rbTcp -> SipTransport.TCP
            R.id.rbTls -> SipTransport.TLS
            else -> SipTransport.UDP
        }

        val account = SipAccount(
            domain = domain,
            port = port,
            username = username,
            password = password,
            displayName = displayName,
            transport = transport,
            stunServer = stunServer
        )

        SipAccount.save(this, account)
        SipManager.registerAccount(account)

        Toast.makeText(this, "Connecting to ${account.fullSipAddress}…", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun disconnectAccount() {
        SipAccount.clear(this)
        SipManager.unregister()
        Toast.makeText(this, "Account disconnected", Toast.LENGTH_SHORT).show()
        finish()
    }
}
