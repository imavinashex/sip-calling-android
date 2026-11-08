package com.imavinashexdev.sipcalling.core

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.imavinashexdev.sipcalling.data.CallLogRepository
import com.imavinashexdev.sipcalling.model.*
import com.imavinashexdev.sipcalling.service.SipForegroundService
import com.imavinashexdev.sipcalling.ui.IncomingCallActivity
import com.imavinashexdev.sipcalling.util.CallerIdResolver
import com.imavinashexdev.sipcalling.util.PhoneNumberNormalizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.*

object SipManager {
    private const val TAG = "SipManager"

    private var appContext: Context? = null
    private var core: Core? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var callDurationJob: Job? = null

    // Registration state flow
    private val _registrationState = MutableStateFlow<RegistrationStatus>(RegistrationStatus.Unregistered)
    val registrationState: StateFlow<RegistrationStatus> = _registrationState.asStateFlow()

    // Active call data flow
    private val _activeCallData = MutableStateFlow<ActiveCallData?>(null)
    val activeCallData: StateFlow<ActiveCallData?> = _activeCallData.asStateFlow()

    // UI event navigation channel
    private val _incomingCallEvents = MutableSharedFlow<ActiveCallData>()
    val incomingCallEvents: SharedFlow<ActiveCallData> = _incomingCallEvents.asSharedFlow()

    private var currentAccount: SipAccount? = null
    private var isSpeakerphoneOn = false

    // Call history tracking
    private var trackingCallType: CallType = CallType.OUTGOING
    private var trackingPhoneNumber: String = ""
    private var trackingDisplayName: String = ""
    private var trackingStartTime: Long = 0L
    private var trackingCallConnected: Boolean = false
    private var trackingDurationSeconds: Long = 0L

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            c: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            Log.d(TAG, "Registration state changed: $state - $message")
            when (state) {
                RegistrationState.Ok -> {
                    val identity = account.params.identityAddress?.asStringUriOnly() ?: "Registered"
                    _registrationState.value = RegistrationStatus.Registered(identity)
                }
                RegistrationState.Progress, RegistrationState.Refreshing -> {
                    _registrationState.value = RegistrationStatus.Registering
                }
                RegistrationState.Failed -> {
                    _registrationState.value = RegistrationStatus.Failed(message.ifEmpty { "Registration error" })
                }
                RegistrationState.Cleared, RegistrationState.None -> {
                    _registrationState.value = RegistrationStatus.Unregistered
                }
            }
        }

        override fun onCallStateChanged(
            c: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.d(TAG, "Call state changed: $state ($message)")
            handleCallStateChange(call, state, message)
        }
    }

    /**
     * Initializes the Linphone Core engine with application context.
     * Binds listening ports and enables keep-alives so incoming calls work seamlessly.
     */
    fun initialize(context: Context) {
        if (core != null) return
        appContext = context.applicationContext

        try {
            val factory = Factory.instance()
            val createdCore = factory.createCore(null, null, context.applicationContext)
            createdCore.addListener(coreListener)
            createdCore.isMicEnabled = true

            // Set media codecs and audio settings
            createdCore.setEchoCancellationEnabled(true)

            // CRITICAL FOR INCOMING CALLS: Bind listening ports!
            // Default on Android SDK is DONTBIND (-2) which rejects all unsolicited incoming SIP packets.
            // Using -1 (RANDOM) binds local listening sockets for UDP, TCP, and TLS.
            val transports = createdCore.transports
            transports.udpPort = -1
            transports.tcpPort = -1
            transports.tlsPort = -1
            createdCore.transports = transports

            // Keep UDP NAT mappings alive on Wi-Fi and mobile networks
            createdCore.isKeepAliveEnabled = true
            createdCore.isNetworkReachable = true

            createdCore.start()
            core = createdCore
            Log.i(TAG, "Liblinphone Core initialized with listening sockets, version: ${createdCore.version}")

            // Auto-register if saved account exists
            val saved = SipAccount.load(context)
            if (saved != null && saved.isValid()) {
                registerAccount(saved)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Liblinphone Core", e)
        }
    }

    /**
     * Registers a SIP account to the configured PBX or SIP server.
     */
    fun registerAccount(account: SipAccount) {
        val c = core ?: run {
            Log.e(TAG, "Core not initialized")
            return
        }

        currentAccount = account
        _registrationState.value = RegistrationStatus.Registering

        try {
            // Clear prior accounts
            c.clearAccounts()
            c.clearAllAuthInfo()

            val factory = Factory.instance()

            // 1. Configure Authentication Info
            val authInfo = factory.createAuthInfo(
                account.username,
                null,
                account.password,
                null,
                null,
                account.domain
            )
            c.addAuthInfo(authInfo)

            // 2. Configure Account Params
            val accountParams = c.createAccountParams()
            val identity = factory.createAddress("sip:${account.username}@${account.domain}")
            if (account.displayName.isNotBlank()) {
                identity?.displayName = account.displayName
            }
            accountParams.identityAddress = identity

            // 3. Configure Server Address with Port & Transport
            val transportParam = account.transport.name.lowercase()
            val cleanDomain = if (account.domain.contains(":")) account.domain.substringBefore(":") else account.domain
            val serverUri = "sip:$cleanDomain:${account.port};transport=$transportParam"
            val serverAddress = factory.createAddress(serverUri)
            accountParams.serverAddress = serverAddress
            accountParams.isRegisterEnabled = true
            accountParams.expires = 300 // 5 minutes registration interval (v1.0.5 setting)

            // 4. Configure STUN Server for NAT traversal only if specified by user (disable ICE which causes PBX 486 busy)
            if (account.stunServer.isNotBlank()) {
                val natPolicy = c.createNatPolicy()
                natPolicy.stunServer = account.stunServer
                natPolicy.isStunEnabled = true
                natPolicy.isIceEnabled = false
                accountParams.natPolicy = natPolicy
            } else {
                accountParams.natPolicy = null
                c.natPolicy = null
            }

            // 5. Create and activate account
            val linphoneAccount = c.createAccount(accountParams)
            c.addAccount(linphoneAccount)
            c.defaultAccount = linphoneAccount

            Log.i(TAG, "Account registered initiated for: ${account.fullSipAddress} on $serverUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register account", e)
            _registrationState.value = RegistrationStatus.Failed(e.localizedMessage ?: "Setup failed")
        }
    }

    /**
     * Unregisters the current SIP account.
     */
    fun unregister() {
        val c = core ?: return
        c.clearAccounts()
        c.clearAllAuthInfo()
        currentAccount = null
        _registrationState.value = RegistrationStatus.Unregistered
    }

    /**
     * Initiates an outgoing audio VoIP call.
     */
    fun makeCall(destination: String, isVideo: Boolean = false): Boolean {
        val c = core ?: return false
        val rawDest = destination.trim()
        if (rawDest.isEmpty()) return false

        val cleanDest = PhoneNumberNormalizer.normalize(rawDest)

        try {
            val address = if (cleanDest.startsWith("sip:", ignoreCase = true) || cleanDest.startsWith("sips:", ignoreCase = true)) {
                Factory.instance().createAddress(cleanDest)
            } else {
                val account = currentAccount ?: appContext?.let { SipAccount.load(it) }
                val domain = account?.domain.orEmpty()
                if (domain.isNotEmpty()) {
                    Factory.instance().createAddress("sip:$cleanDest@$domain")
                } else {
                    Factory.instance().createAddress(cleanDest)
                }
            } ?: run {
                Log.e(TAG, "Invalid address format: $cleanDest")
                return false
            }

            val params = c.createCallParams(null) ?: return false
            params.setAudioEnabled(true)
            params.setVideoEnabled(isVideo)
            if (c.defaultAccount != null) {
                params.account = c.defaultAccount
            }
            c.isMicEnabled = true

            val call = c.inviteAddressWithParams(address, params)
            if (call != null) {
                Log.i(TAG, "Outgoing call initiated to: ${address.asStringUriOnly()}")
                startActiveForegroundService(address.asStringUriOnly())
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating call", e)
        }
        return false
    }

    /**
     * Accepts the currently incoming call.
     */
    fun acceptCall(): Boolean {
        val c = core ?: return false
        val call = c.currentCall ?: c.calls.firstOrNull() ?: return false
        try {
            c.isMicEnabled = true
            call.accept()
            Log.i(TAG, "Incoming call accepted")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call", e)
        }
        return false
    }

    /**
     * Terminates or declines the current active/incoming call.
     */
    fun terminateCall() {
        val c = core ?: return
        val call = c.currentCall ?: c.calls.firstOrNull()
        try {
            call?.terminate()
        } catch (e: Exception) {
            Log.e(TAG, "Error terminating call", e)
        }
        stopForegroundService()
    }

    /**
     * Toggles microphone mute.
     */
    fun toggleMute(): Boolean {
        val c = core ?: return false
        val isMuted = !c.isMicEnabled
        c.isMicEnabled = isMuted
        val current = _activeCallData.value
        if (current != null) {
            _activeCallData.value = current.copy(isMuted = !c.isMicEnabled)
        }
        return !c.isMicEnabled
    }

    /**
     * Toggles speakerphone on/off.
     */
    fun toggleSpeaker(context: Context): Boolean {
        isSpeakerphoneOn = !isSpeakerphoneOn
        setSpeakerphone(context, isSpeakerphoneOn)
        return isSpeakerphoneOn
    }

    /**
     * Routes audio output to Speaker or Earpiece across both Liblinphone SDK and Android AudioManager.
     */
    fun setSpeakerphone(context: Context, enableSpeaker: Boolean) {
        isSpeakerphoneOn = enableSpeaker
        val c = core
        val call = c?.currentCall ?: c?.calls?.firstOrNull()

        // 1. Android AudioManager switching
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val availableDevices = audioManager.availableCommunicationDevices
                    val targetType = if (enableSpeaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    val selectedDevice = availableDevices.firstOrNull { it.type == targetType }
                    if (selectedDevice != null) {
                        audioManager.setCommunicationDevice(selectedDevice)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = enableSpeaker
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = enableSpeaker
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure AudioManager speaker: $enableSpeaker", e)
            }
        }

        // 2. Liblinphone audio device switching
        if (c != null && call != null) {
            try {
                val audioDevices = c.audioDevices
                val targetDeviceType = if (enableSpeaker) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
                val targetDevice = audioDevices.firstOrNull { it.type == targetDeviceType }

                if (targetDevice != null) {
                    call.outputAudioDevice = targetDevice
                    Log.i(TAG, "Linphone output audio device set to: ${targetDevice.deviceName} ($targetDeviceType)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure Linphone audio device", e)
            }
        }

        // Update StateFlow
        val current = _activeCallData.value
        if (current != null) {
            _activeCallData.value = current.copy(isSpeakerOn = enableSpeaker)
        }
    }

    /**
     * Toggles hold/resume on the active call.
     */
    fun toggleHold(): Boolean {
        val call = core?.currentCall ?: core?.calls?.firstOrNull() ?: return false
        val currentlyOnHold = _activeCallData.value?.isOnHold == true
        val newHoldState = !currentlyOnHold

        return if (!newHoldState) {
            try {
                call.resume()
                Log.i(TAG, "Resumed call: ${call.remoteAddress.asStringUriOnly()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming call", e)
            }
            false
        } else {
            try {
                call.pause()
                Log.i(TAG, "Paused call: ${call.remoteAddress.asStringUriOnly()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing call", e)
            }
            true
        }

        val current = _activeCallData.value
        if (current != null) {
            _activeCallData.value = current.copy(
                isOnHold = newHoldState,
                state = if (newHoldState) VoipCallState.Paused else VoipCallState.Connected
            )
        }
        return newHoldState
    }

    /**
     * Sends DTMF digit (0-9, *, #) across active RTP stream.
     */
    fun sendDtmf(dtmfChar: Char) {
        val call = core?.currentCall ?: return
        try {
            call.sendDtmf(dtmfChar)
            Log.d(TAG, "Sent DTMF: $dtmfChar")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send DTMF", e)
        }
    }

    private fun handleCallStateChange(call: Call, state: Call.State, message: String) {
        val rawRemoteAddress = call.remoteAddress.asStringUriOnly()
        val rawDisplayName = call.remoteAddress.displayName?.ifEmpty { null }
        Log.d(TAG, "Call state changed: $state, remote: $rawRemoteAddress, message: $message")

        val callerInfo = CallerIdResolver.resolve(rawRemoteAddress, rawDisplayName)
        val remoteDisplayName = callerInfo.name
        val remoteAddress = callerInfo.number

        when (state) {
            Call.State.IncomingReceived -> {
                trackingCallType = CallType.INCOMING
                trackingPhoneNumber = callerInfo.number
                trackingDisplayName = callerInfo.name
                trackingStartTime = System.currentTimeMillis()
                trackingCallConnected = false
                trackingDurationSeconds = 0L

                val data = ActiveCallData(
                    remoteAddress = remoteAddress,
                    remoteDisplayName = remoteDisplayName,
                    state = VoipCallState.IncomingReceived
                )
                _activeCallData.value = data

                // 1. Start Foreground Service with Incoming Call Action (Full-screen intent + heads up notification)
                startIncomingForegroundService(remoteDisplayName, remoteAddress)

                // 2. Launch Incoming Call Activity directly if possible
                appContext?.let { ctx ->
                    try {
                        val intent = Intent(ctx, IncomingCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("CALLER_NAME", remoteDisplayName)
                            putExtra("CALLER_URI", remoteAddress)
                        }
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct activity launch restricted by background policy, relying on FullScreenIntent notification", e)
                    }
                }

                scope.launch {
                    _incomingCallEvents.emit(data)
                }
            }

            Call.State.OutgoingInit -> {
                trackingCallType = CallType.OUTGOING
                trackingPhoneNumber = callerInfo.number
                trackingDisplayName = callerInfo.name
                trackingStartTime = System.currentTimeMillis()
                trackingCallConnected = false
                trackingDurationSeconds = 0L

                _activeCallData.value = ActiveCallData(
                    remoteAddress = callerInfo.number,
                    remoteDisplayName = callerInfo.name,
                    state = VoipCallState.OutgoingInit
                )
            }

            Call.State.OutgoingProgress, Call.State.OutgoingRinging -> {
                _activeCallData.value = _activeCallData.value?.copy(
                    state = VoipCallState.OutgoingRinging
                ) ?: ActiveCallData(
                    remoteAddress = remoteAddress,
                    remoteDisplayName = remoteDisplayName,
                    state = VoipCallState.OutgoingRinging
                )
            }

            Call.State.Connected, Call.State.StreamsRunning -> {
                core?.isMicEnabled = true
                trackingCallConnected = true
                val codec = call.currentParams.usedAudioPayloadType?.mimeType ?: "Opus"
                _activeCallData.value = _activeCallData.value?.copy(
                    state = VoipCallState.Connected,
                    codec = codec,
                    isOnHold = false
                ) ?: ActiveCallData(
                    remoteAddress = remoteAddress,
                    remoteDisplayName = remoteDisplayName,
                    state = VoipCallState.Connected,
                    codec = codec,
                    isOnHold = false
                )
                startCallTimer()
                startActiveForegroundService(remoteDisplayName)
                appContext?.let { ctx ->
                    setSpeakerphone(ctx, isSpeakerphoneOn)
                }
            }

            Call.State.Paused, Call.State.PausedByRemote -> {
                _activeCallData.value = _activeCallData.value?.copy(
                    state = VoipCallState.Paused,
                    isOnHold = true
                )
            }

            Call.State.Resuming -> {
                _activeCallData.value = _activeCallData.value?.copy(
                    state = VoipCallState.Connected,
                    isOnHold = false
                )
            }

            Call.State.End, Call.State.Released, Call.State.Error -> {
                stopCallTimer()
                resetAudioRouting()

                if (trackingPhoneNumber.isNotBlank()) {
                    val finalType = if (trackingCallType == CallType.INCOMING) {
                        if (trackingCallConnected) CallType.INCOMING else CallType.MISSED
                    } else {
                        CallType.OUTGOING
                    }
                    val entry = CallLogEntry(
                        phoneNumber = trackingPhoneNumber,
                        displayName = trackingDisplayName,
                        type = finalType,
                        timestamp = if (trackingStartTime > 0) trackingStartTime else System.currentTimeMillis(),
                        durationSeconds = trackingDurationSeconds
                    )
                    CallLogRepository.insertCall(entry)
                    trackingPhoneNumber = ""
                }

                _activeCallData.value = _activeCallData.value?.copy(
                    state = VoipCallState.Terminated
                )
                scope.launch {
                    delay(1200)
                    _activeCallData.value = null
                }
                stopForegroundService()
            }

            else -> Unit
        }
    }

    private fun startCallTimer() {
        if (callDurationJob?.isActive == true) return
        callDurationJob = scope.launch {
            var seconds = trackingDurationSeconds
            while (isActive) {
                delay(1000)
                seconds++
                trackingDurationSeconds = seconds
                val current = _activeCallData.value ?: break
                _activeCallData.value = current.copy(durationSeconds = seconds)
            }
        }
    }

    private fun stopCallTimer() {
        callDurationJob?.cancel()
        callDurationJob = null
    }

    private fun startIncomingForegroundService(callerName: String, callerUri: String) {
        val ctx = appContext ?: return
        val serviceIntent = Intent(ctx, SipForegroundService::class.java).apply {
            action = SipForegroundService.ACTION_INCOMING_CALL
            putExtra(SipForegroundService.EXTRA_CALLER_NAME, callerName)
            putExtra(SipForegroundService.EXTRA_CALLER_URI, callerUri)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(serviceIntent)
        } else {
            ctx.startService(serviceIntent)
        }
    }

    private fun startActiveForegroundService(callerText: String) {
        val ctx = appContext ?: return
        val serviceIntent = Intent(ctx, SipForegroundService::class.java).apply {
            action = SipForegroundService.ACTION_START_CALL
            putExtra(SipForegroundService.EXTRA_CALLER_NAME, callerText)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(serviceIntent)
        } else {
            ctx.startService(serviceIntent)
        }
    }

    private fun stopForegroundService() {
        val ctx = appContext ?: return
        val serviceIntent = Intent(ctx, SipForegroundService::class.java).apply {
            action = SipForegroundService.ACTION_STOP_CALL
        }
        ctx.startService(serviceIntent)
    }

    private fun resetAudioRouting() {
        isSpeakerphoneOn = false
        appContext?.let { ctx ->
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        audioManager.clearCommunicationDevice()
                    }
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting audio routing on call end", e)
                }
            }
        }
    }

    /**
     * Refreshes Linphone sockets and registrations when app resumes to foreground.
     */
    fun onAppResume() {
        val c = core ?: return
        try {
            c.enterForeground()
            c.isNetworkReachable = true
            if (c.callsNb == 0 && c.currentCall == null) {
                c.refreshRegisters()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in onAppResume", e)
        }
    }

    /**
     * Prepares Linphone sockets for background execution.
     * CRITICAL: Never enter background if a call is in progress!
     * Calling enterBackground() causes Liblinphone to suspend audio devices and microphone capture.
     */
    fun onAppPause() {
        val c = core ?: return
        if (c.callsNb > 0 || c.currentCall != null) {
            Log.i(TAG, "Active call in progress (${c.callsNb} calls), skipping enterBackground to keep microphone alive")
            return
        }
        try {
            c.enterBackground()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onAppPause", e)
        }
    }

    fun destroy() {
        core?.removeListener(coreListener)
        core?.stop()
        core = null
        appContext = null
    }
}
