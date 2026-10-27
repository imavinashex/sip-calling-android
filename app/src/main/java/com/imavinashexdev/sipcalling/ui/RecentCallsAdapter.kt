package com.imavinashexdev.sipcalling.ui

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.imavinashexdev.sipcalling.R
import com.imavinashexdev.sipcalling.databinding.ItemCallLogBinding
import com.imavinashexdev.sipcalling.model.CallLogEntry
import com.imavinashexdev.sipcalling.model.CallType
import com.imavinashexdev.sipcalling.util.CallerIdResolver
import java.text.SimpleDateFormat
import java.util.*

class RecentCallsAdapter(
    private val onCallClick: (CallLogEntry) -> Unit,
    private val onLongClick: (CallLogEntry) -> Unit
) : ListAdapter<CallLogEntry, RecentCallsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCallLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: CallLogEntry) {
            val context = binding.root.context

            // Dynamic Caller ID lookup against Contacts
            val callerInfo = CallerIdResolver.resolve(entry.phoneNumber, entry.displayName)
            val displayName = callerInfo.name
            binding.tvNameOrNumber.text = displayName

            // Avatar Initials (up to 2 chars)
            val initials = if (entry.displayTitle.isNotBlank()) {
                val clean = entry.displayTitle.trimStart('+', '0')
                if (clean.length >= 2) clean.substring(0, 2) else clean
            } else {
                "#"
            }
            binding.tvAvatarInitials.text = initials.uppercase(Locale.getDefault())

            // Call Type & Duration
            when (entry.type) {
                CallType.OUTGOING -> {
                    binding.ivCallType.setImageResource(R.drawable.ic_call_made)
                    binding.ivCallType.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.call_green)
                    )
                    val durationStr = if (entry.durationSeconds > 0) " · ${formatDuration(entry.durationSeconds)}" else ""
                    binding.tvCallDetails.text = "Outgoing$durationStr"
                    binding.tvCallDetails.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                CallType.INCOMING -> {
                    binding.ivCallType.setImageResource(R.drawable.ic_call_received)
                    binding.ivCallType.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.primary)
                    )
                    val durationStr = if (entry.durationSeconds > 0) " · ${formatDuration(entry.durationSeconds)}" else ""
                    binding.tvCallDetails.text = "Incoming$durationStr"
                    binding.tvCallDetails.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                CallType.MISSED -> {
                    binding.ivCallType.setImageResource(R.drawable.ic_call_missed)
                    binding.ivCallType.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.call_red)
                    )
                    binding.tvCallDetails.text = "Missed Call"
                    binding.tvCallDetails.setTextColor(ContextCompat.getColor(context, R.color.call_red))
                }
            }

            // Timestamp formatting
            binding.tvTimestamp.text = formatTimestamp(entry.timestamp)

            // Click actions
            binding.root.setOnClickListener { onCallClick(entry) }
            binding.btnCallBack.setOnClickListener { onCallClick(entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry)
                true
            }
        }

        private fun formatDuration(seconds: Long): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format("%02d:%02d", mins, secs)
        }

        private fun formatTimestamp(timeMillis: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timeMillis

            return when {
                diff < 60 * 1000L -> "Just now"
                DateUtils.isToday(timeMillis) -> {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMillis))
                }
                diff < 48 * 60 * 60 * 1000L && DateUtils.isToday(timeMillis + 24 * 60 * 60 * 1000L) -> {
                    "Yesterday"
                }
                else -> {
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timeMillis))
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<CallLogEntry>() {
        override fun areItemsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean =
            oldItem == newItem
    }
}
