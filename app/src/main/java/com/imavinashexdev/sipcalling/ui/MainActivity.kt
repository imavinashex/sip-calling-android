package com.imavinashexdev.sipcalling.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
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
    private var isKeyboardMode = false
    private var wasImeVisible = false

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
        } else {
            checkBatteryOptimization()
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

    override fun onResume() {
        super.onResume()
        SipManager.onAppResume()
    }

    override fun onPause() {
        super.onPause()
        SipManager.onAppPause()
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
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to request battery optimization exemption", e)
                }
            }
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

        // Long click '0' adds '+'
        binding.key0.root.setOnLongClickListener {
            binding.key0.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            appendDigit("+")
            true
        }

        binding.btnBackspace.setOnClickListener {
            binding.btnBackspace.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            removeLastDigit()
        }

        binding.btnBackspace.setOnLongClickListener {
            binding.btnBackspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            binding.etTargetNumber.setText("")
            true
        }
    }

    private fun appendDigit(digit: String) {
        val current = binding.etTargetNumber.text?.toString().orEmpty()
        val cursorPosition = binding.etTargetNumber.selectionStart.coerceAtLeast(0)
        val updated = StringBuilder(current).insert(cursorPosition, digit).toString()
        binding.etTargetNumber.setText(updated)
        binding.etTargetNumber.setSelection((cursorPosition + digit.length).coerceAtMost(updated.length))
    }

    private fun removeLastDigit() {
        val current = binding.etTargetNumber.text?.toString().orEmpty()
        val cursorPosition = binding.etTargetNumber.selectionStart
        if (cursorPosition > 0 && current.isNotEmpty()) {
            val updated = StringBuilder(current).deleteCharAt(cursorPosition - 1).toString()
            binding.etTargetNumber.setText(updated)
            binding.etTargetNumber.setSelection(cursorPosition - 1)
        }
    }

    private fun setupActions() {
        // Primary mode is dialpad: disable soft keyboard auto-popup on focus
        binding.etTargetNumber.showSoftInputOnFocus = false

        // Toggle button switches between Dialpad and Soft Keyboard
        binding.btnKeyboardToggle.setOnClickListener {
            setKeyboardMode(!isKeyboardMode)
        }

        // Tapping the input box or display container smoothly switches to keyboard mode
        binding.etTargetNumber.setOnClickListener {
            if (!isKeyboardMode) {
                setKeyboardMode(true)
            }
        }
        binding.displayContainer.setOnClickListener {
            if (!isKeyboardMode) {
                setKeyboardMode(true)
            }
        }
        binding.etTargetNumber.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && !isKeyboardMode) {
                setKeyboardMode(true)
            }
            false
        }

        // Detect soft keyboard dismiss (down arrow / IME close) to smoothly bring back the dialpad
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !imeVisible && isKeyboardMode) {
                setKeyboardMode(false)
            }
            wasImeVisible = imeVisible
            insets
        }

        // Intercept back button when in keyboard mode to restore the primary dialpad view
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isKeyboardMode) {
                    setKeyboardMode(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        binding.etTargetNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND) {
                initiateCall()
                true
            } else {
                false
            }
        }

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

    private fun setKeyboardMode(enableKeyboard: Boolean, animate: Boolean = true) {
        if (isKeyboardMode == enableKeyboard) return
        isKeyboardMode = enableKeyboard

        if (animate) {
            val transition = AutoTransition().apply {
                duration = 200
                ordering = TransitionSet.ORDERING_TOGETHER
            }
            TransitionManager.beginDelayedTransition(binding.layoutDialer, transition)
        }

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (enableKeyboard) {
            binding.dialpadContainer.visibility = View.GONE
            binding.btnKeyboardToggle.setImageResource(R.drawable.ic_dialpad)
            binding.btnKeyboardToggle.contentDescription = getString(R.string.switch_to_dialpad)
            binding.etTargetNumber.showSoftInputOnFocus = true
            binding.etTargetNumber.requestFocus()
            imm?.showSoftInput(binding.etTargetNumber, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.dialpadContainer.visibility = View.VISIBLE
            binding.btnKeyboardToggle.setImageResource(R.drawable.ic_keyboard)
            binding.btnKeyboardToggle.contentDescription = getString(R.string.switch_to_keyboard)
            binding.etTargetNumber.showSoftInputOnFocus = false
            imm?.hideSoftInputFromWindow(binding.etTargetNumber.windowToken, 0)
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
            .setMessage(getString(R.string.delete_call_confirm))
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

    private fun setupTabs() {
        binding.tabDialer.setOnClickListener { switchTab(MainTab.DIALER) }
        binding.tabRecents.setOnClickListener { switchTab(MainTab.RECENTS) }
        binding.tabContacts.setOnClickListener { switchTab(MainTab.CONTACTS) }
        switchTab(MainTab.DIALER)
    }

    private enum class MainTab { DIALER, RECENTS, CONTACTS }

    private fun switchTab(tab: MainTab) {
        if (tab != MainTab.DIALER && isKeyboardMode) {
            setKeyboardMode(false, animate = false)
        }

        binding.layoutDialer.visibility = if (tab == MainTab.DIALER) View.VISIBLE else View.GONE
        binding.layoutRecents.visibility = if (tab == MainTab.RECENTS) View.VISIBLE else View.GONE
        binding.layoutContacts.visibility = if (tab == MainTab.CONTACTS) View.VISIBLE else View.GONE

        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val hintColor = ContextCompat.getColor(this, R.color.text_hint)

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
