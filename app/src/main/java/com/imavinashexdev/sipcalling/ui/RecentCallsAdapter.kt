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

            // Avatar Initials derived cleanly from resolved name or number (Never "SI")
            val initials = generateInitials(displayName)
            binding.tvAvatarInitials.text = initials

            // Call Type & Duration
            val callTypePrefix = if (callerInfo.hasContactMatch && callerInfo.number.isNotBlank()) {
                "${callerInfo.number} · "
            } else {
                ""
            }

            when (entry.type) {
                CallType.OUTGOING -> {
                    binding.ivCallType.setImageResource(R.drawable.ic_call_made)
                    binding.ivCallType.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.call_green)
                    )
                    val durationStr = if (entry.durationSeconds > 0) " · ${formatDuration(entry.durationSeconds)}" else ""
                    binding.tvCallDetails.text = "${callTypePrefix}Outgoing$durationStr"
                    binding.tvCallDetails.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                CallType.INCOMING -> {
                    binding.ivCallType.setImageResource(R.drawable.ic_call_received)
                    binding.ivCallType.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.primary)
                    )
                    val durationStr = if (entry.durationSeconds > 0) " · ${formatDuration(entry.durationSeconds)}" else ""
                    binding.tvCallDetails.text = "${callTypePrefix}Incoming$durationStr"
                    binding.tvCallDetails.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                CallType.MISSED -> {
                    binding.ivCallType.setImageResource(R.drawable.ic_call_missed)
                    binding.ivCallType.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.call_red)
                    )
                    binding.tvCallDetails.text = "${callTypePrefix}Missed Call"
                    binding.tvCallDetails.setTextColor(ContextCompat.getColor(context, R.color.call_red))
                }
            }

            // Timestamp formatting
            binding.tvTimestamp.text = formatTimestamp(entry.timestamp)

            // Click actions - call back using clean resolved number or entry's dialable target
            binding.root.setOnClickListener { onCallClick(entry) }
            binding.btnCallBack.setOnClickListener { onCallClick(entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry)
                true
            }
        }

        private fun generateInitials(title: String): String {
            val clean = title.trimStart('+', '0').trim()
            if (clean.isBlank()) return "#"

            val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.getDefault())
                clean.length >= 2 -> clean.substring(0, 2).uppercase(Locale.getDefault())
                else -> clean.uppercase(Locale.getDefault())
            }
        }

        private fun formatDuration(seconds: Long): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return "%02d:%02d".format(mins, secs)
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < DateUtils.MINUTE_IN_MILLIS -> "Just now"
                DateUtils.isToday(timestamp) -> {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
                }
                diff < DateUtils.WEEK_IN_MILLIS -> {
                    SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(timestamp))
                }
                else -> {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
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
