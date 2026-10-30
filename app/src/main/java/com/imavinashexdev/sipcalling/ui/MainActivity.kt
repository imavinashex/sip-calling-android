package com.imavinashexdev.sipcalling.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.imavinashexdev.sipcalling.R
import com.imavinashexdev.sipcalling.core.SipManager
import com.imavinashexdev.sipcalling.data.CallLogRepository
import com.imavinashexdev.sipcalling.data.ContactRepository
import com.imavinashexdev.sipcalling.databinding.ActivityMainBinding
import com.imavinashexdev.sipcalling.databinding.DialogAddContactBinding
import com.imavinashexdev.sipcalling.model.CallLogEntry
import com.imavinashexdev.sipcalling.model.Contact
import com.imavinashexdev.sipcalling.model.RegistrationStatus
import com.imavinashexdev.sipcalling.model.VoipCallState
import com.imavinashexdev.sipcalling.util.PhoneNumberNormalizer
import com.imavinashexdev.sipcalling.util.SoundManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentCallsAdapter: RecentCallsAdapter
    private lateinit var contactsAdapter: ContactsAdapter

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            importContacts()
        } else {
            Toast.makeText(this, "Contacts permission required to import", Toast.LENGTH_SHORT).show()
        }
    }

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
        setupRecentCalls()
        setupContacts()
        setupTabs()
        observeRegistrationState()
        observeActiveCall()
        observeRecentCalls()
        observeContacts()
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
            root.isHapticFeedbackEnabled = true
            root.setOnClickListener {
                root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
            binding.key0.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            appendDigit("+")
            true
        }

        // Backspace button
        binding.btnBackspace.isHapticFeedbackEnabled = true
        binding.btnBackspace.setOnClickListener {
            binding.btnBackspace.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val text = binding.etTargetNumber.text
            if (!text.isNullOrEmpty()) {
                val start = binding.etTargetNumber.selectionStart
                val end = binding.etTargetNumber.selectionEnd
                if (start > 0 && start == end) {
                    text.delete(start - 1, start)
                } else if (start != end) {
                    text.delete(start, end)
                } else {
                    text.delete(text.length - 1, text.length)
                }
            }
        }

        binding.btnBackspace.setOnLongClickListener {
            binding.btnBackspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            binding.etTargetNumber.text?.clear()
            true
        }
    }

    private fun appendDigit(digit: String) {
        binding.etTargetNumber.append(digit)
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
        val rawDestination = binding.etTargetNumber.text?.toString()?.trim().orEmpty()
        if (rawDestination.isEmpty()) {
            Toast.makeText(this, "Please enter a phone number or SIP address", Toast.LENGTH_SHORT).show()
            return
        }
        callNumber(rawDestination)
    }

    private fun callNumber(destination: String) {
        val normalized = PhoneNumberNormalizer.normalize(destination)
        binding.etTargetNumber.setText(normalized)
        binding.etTargetNumber.setSelection(normalized.length)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
            return
        }

        val success = SipManager.makeCall(normalized)
        if (success) {
            val callIntent = Intent(this, CallActivity::class.java)
            startActivity(callIntent)
        } else {
            Toast.makeText(this, "Failed to initiate call. Check SIP account settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecentCalls() {
        recentCallsAdapter = RecentCallsAdapter(
            onCallClick = { entry ->
                callNumber(entry.phoneNumber)
            },
            onLongClick = { entry ->
                showDeleteEntryDialog(entry)
            }
        )

        binding.rvRecentCalls.layoutManager = LinearLayoutManager(this)
        binding.rvRecentCalls.adapter = recentCallsAdapter

        binding.btnClearHistory.setOnClickListener {
            showClearAllDialog()
        }
    }

    private fun showDeleteEntryDialog(entry: CallLogEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_call_log)
            .setMessage("${getString(R.string.delete_call_confirm)}\n${entry.displayTitle}")
            .setPositiveButton(R.string.delete_call_log) { _, _ ->
                CallLogRepository.deleteCall(entry.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history)
            .setMessage(R.string.clear_history_confirm)
            .setPositiveButton(R.string.clear_history) { _, _ ->
                CallLogRepository.clearAll()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    enum class MainTab {
        DIALER,
        RECENTS,
        CONTACTS
    }

    private fun setupTabs() {
        binding.tabDialer.setOnClickListener { switchTab(MainTab.DIALER) }
        binding.tabRecents.setOnClickListener { switchTab(MainTab.RECENTS) }
        binding.tabContacts.setOnClickListener { switchTab(MainTab.CONTACTS) }
    }

    private fun switchTab(tab: MainTab) {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val hintColor = ContextCompat.getColor(this, R.color.text_hint)

        binding.layoutDialer.visibility = if (tab == MainTab.DIALER) View.VISIBLE else View.GONE
        binding.layoutRecents.visibility = if (tab == MainTab.RECENTS) View.VISIBLE else View.GONE
        binding.layoutContacts.visibility = if (tab == MainTab.CONTACTS) View.VISIBLE else View.GONE

        // Tab Dialer
        val isDialer = tab == MainTab.DIALER
        binding.ivTabDialer.imageTintList = ColorStateList.valueOf(if (isDialer) primaryColor else hintColor)
        binding.tvTabDialer.setTextColor(if (isDialer) primaryColor else hintColor)

        // Tab Recents
        val isRecents = tab == MainTab.RECENTS
        binding.ivTabRecents.imageTintList = ColorStateList.valueOf(if (isRecents) primaryColor else hintColor)
        binding.tvTabRecents.setTextColor(if (isRecents) primaryColor else hintColor)

        // Tab Contacts
        val isContacts = tab == MainTab.CONTACTS
        binding.ivTabContacts.imageTintList = ColorStateList.valueOf(if (isContacts) primaryColor else hintColor)
        binding.tvTabContacts.setTextColor(if (isContacts) primaryColor else hintColor)
    }

    private fun setupContacts() {
        contactsAdapter = ContactsAdapter(
            onCallClick = { contact ->
                callNumber(contact.phoneNumber)
            },
            onLongClick = { contact ->
                showDeleteContactDialog(contact)
            }
        )

        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = contactsAdapter

        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        binding.btnImportContacts.setOnClickListener {
            checkContactsPermissionAndImport()
        }

        binding.btnEmptyImport.setOnClickListener {
            checkContactsPermissionAndImport()
        }

        binding.etSearchContacts.addTextChangedListener { text ->
            val query = text?.toString().orEmpty()
            val filtered = ContactRepository.search(query)
            contactsAdapter.submitList(filtered)
        }
    }

    private fun checkContactsPermissionAndImport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            importContacts()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun importContacts() {
        Toast.makeText(this, "Importing & normalizing contacts…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val count = ContactRepository.importDeviceContacts(this@MainActivity)
            Toast.makeText(this@MainActivity, getString(R.string.contacts_imported, count), Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddContactDialog() {
        val dialogBinding = DialogAddContactBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancelContact.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveContact.setOnClickListener {
            val name = dialogBinding.etContactName.text?.toString()?.trim().orEmpty()
            val number = dialogBinding.etContactNumber.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                dialogBinding.tilContactName.error = "Name is required"
                return@setOnClickListener
            }
            if (number.isEmpty()) {
                dialogBinding.tilContactNumber.error = "Phone number is required"
                return@setOnClickListener
            }

            ContactRepository.insertContact(name, number)
            dialog.dismiss()
            Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showDeleteContactDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_contact)
            .setMessage("${getString(R.string.delete_contact_confirm)}\n${contact.name} (${contact.phoneNumber})")
            .setPositiveButton(R.string.delete_contact) { _, _ ->
                ContactRepository.deleteContact(contact.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observeContacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ContactRepository.contacts.collectLatest { list ->
                    val query = binding.etSearchContacts.text?.toString().orEmpty()
                    val toDisplay = if (query.isBlank()) list else ContactRepository.search(query)
                    contactsAdapter.submitList(toDisplay)
                    if (list.isEmpty()) {
                        binding.layoutEmptyContacts.visibility = View.VISIBLE
                        binding.rvContacts.visibility = View.GONE
                    } else {
                        binding.layoutEmptyContacts.visibility = View.GONE
                        binding.rvContacts.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun observeRecentCalls() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallLogRepository.recentCalls.collectLatest { list ->
                    recentCallsAdapter.submitList(list)
                    if (list.isEmpty()) {
                        binding.layoutEmptyRecents.visibility = View.VISIBLE
                        binding.rvRecentCalls.visibility = View.GONE
                    } else {
                        binding.layoutEmptyRecents.visibility = View.GONE
                        binding.rvRecentCalls.visibility = View.VISIBLE
                    }
                }
            }
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
