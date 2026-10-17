package com.antigravity.sipcalling.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.antigravity.sipcalling.R
import com.antigravity.sipcalling.core.SipManager
import com.antigravity.sipcalling.databinding.ActivityCallBinding
import com.antigravity.sipcalling.databinding.DialogDtmfBinding
import com.antigravity.sipcalling.model.ActiveCallData
import com.antigravity.sipcalling.model.VoipCallState
import com.antigravity.sipcalling.util.SoundManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var dtmfDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        observeCallData()
    }

    private fun setupButtons() {
        binding.btnEndCall.setOnClickListener {
            SipManager.terminateCall()
            finish()
        }

        binding.btnMute.setOnClickListener {
            val isMuted = SipManager.toggleMute()
            updateMuteButton(isMuted)
        }

        binding.btnSpeaker.setOnClickListener {
            val isSpeakerOn = SipManager.toggleSpeaker(this)
            updateSpeakerButton(isSpeakerOn)
        }

        binding.btnHold.setOnClickListener {
            val isOnHold = SipManager.toggleHold()
            updateHoldButton(isOnHold)
        }

        binding.btnKeypad.setOnClickListener {
            showDtmfDialog()
        }
    }

    private fun observeCallData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SipManager.activeCallData.collectLatest { callData ->
                    if (callData == null) {
                        finish()
                        return@collectLatest
                    }
                    updateUi(callData)
                }
            }
        }
    }

    private fun updateUi(call: ActiveCallData) {
        binding.tvCallerName.text = call.remoteDisplayName
        binding.tvCallerUri.text = call.remoteAddress

        when (call.state) {
            VoipCallState.OutgoingInit -> {
                binding.tvCallState.text = getString(R.string.calling)
                binding.tvCallDuration.visibility = View.GONE
            }

            VoipCallState.OutgoingProgress, VoipCallState.OutgoingRinging -> {
                binding.tvCallState.text = getString(R.string.ringing)
                binding.tvCallDuration.visibility = View.GONE
            }

            VoipCallState.Connected, VoipCallState.StreamsRunning -> {
                binding.tvCallState.text = getString(R.string.call_connected)
                binding.tvCallDuration.visibility = View.VISIBLE
                binding.tvCallDuration.text = formatDuration(call.durationSeconds)

                if (call.codec.isNotBlank()) {
                    binding.tvCodecInfo.visibility = View.VISIBLE
                    binding.tvCodecInfo.text = "${call.codec} HD Audio"
                }
            }

            VoipCallState.Paused -> {
                binding.tvCallState.text = getString(R.string.call_on_hold)
            }

            VoipCallState.Terminated, VoipCallState.Ending -> {
                binding.tvCallState.text = getString(R.string.call_ended)
            }

            else -> Unit
        }

        updateMuteButton(call.isMuted)
        updateSpeakerButton(call.isSpeakerOn)
        updateHoldButton(call.isOnHold)
    }

    private fun updateMuteButton(isMuted: Boolean) {
        binding.btnMute.isSelected = isMuted
        val colorRes = if (isMuted) R.color.call_btn_active_tint else R.color.call_btn_inactive_tint
        binding.btnMute.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        binding.btnMute.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic)
        binding.tvMuteLabel.text = if (isMuted) "Unmute" else getString(R.string.action_mute)
    }

    private fun updateSpeakerButton(isSpeakerOn: Boolean) {
        binding.btnSpeaker.isSelected = isSpeakerOn
        val colorRes = if (isSpeakerOn) R.color.call_btn_active_tint else R.color.call_btn_inactive_tint
        binding.btnSpeaker.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun updateHoldButton(isOnHold: Boolean) {
        binding.btnHold.isSelected = isOnHold
        val colorRes = if (isOnHold) R.color.call_btn_active_tint else R.color.call_btn_inactive_tint
        binding.btnHold.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        binding.tvHoldLabel.text = if (isOnHold) "Resume" else getString(R.string.action_hold)
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun showDtmfDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogDtmfBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val digitsBuilder = StringBuilder()

        val setupKey = { root: View, digit: String ->
            val tvDigit = root.findViewById<TextView>(R.id.tvDigit)
            val tvLetters = root.findViewById<TextView>(R.id.tvLetters)
            tvDigit.text = digit
            tvLetters.visibility = View.GONE
            root.setOnClickListener {
                val char = digit.first()
                SoundManager.playDtmfTone(char)
                SipManager.sendDtmf(char)
                digitsBuilder.append(char)
                dialogBinding.tvDtmfDigits.text = digitsBuilder.toString()
            }
        }

        setupKey(dialogBinding.dtmf1.root, "1")
        setupKey(dialogBinding.dtmf2.root, "2")
        setupKey(dialogBinding.dtmf3.root, "3")
        setupKey(dialogBinding.dtmf4.root, "4")
        setupKey(dialogBinding.dtmf5.root, "5")
        setupKey(dialogBinding.dtmf6.root, "6")
        setupKey(dialogBinding.dtmf7.root, "7")
        setupKey(dialogBinding.dtmf8.root, "8")
        setupKey(dialogBinding.dtmf9.root, "9")
        setupKey(dialogBinding.dtmfStar.root, "*")
        setupKey(dialogBinding.dtmf0.root, "0")
        setupKey(dialogBinding.dtmfHash.root, "#")

        dialogBinding.btnDtmfClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dtmfDialog = dialog
    }

    override fun onDestroy() {
        dtmfDialog?.dismiss()
        super.onDestroy()
    }
}
