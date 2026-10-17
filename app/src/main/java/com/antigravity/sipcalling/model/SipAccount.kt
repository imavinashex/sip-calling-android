package com.antigravity.sipcalling.model

import android.content.Context
import android.content.SharedPreferences

enum class SipTransport {
    UDP,
    TCP,
    TLS
}

data class SipAccount(
    val domain: String,
    val port: Int = 5060,
    val username: String,
    val password: String,
    val displayName: String = "",
    val transport: SipTransport = SipTransport.UDP,
    val stunServer: String = "stun.linphone.org"
) {
    val fullSipAddress: String
        get() = "sip:$username@$domain"

    fun isValid(): Boolean {
        return domain.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    companion object {
        private const val PREFS_NAME = "sip_account_prefs"
        private const val KEY_DOMAIN = "sip_domain"
        private const val KEY_PORT = "sip_port"
        private const val KEY_USERNAME = "sip_username"
        private const val KEY_PASSWORD = "sip_password"
        private const val KEY_DISPLAY_NAME = "sip_display_name"
        private const val KEY_TRANSPORT = "sip_transport"
        private const val KEY_STUN = "sip_stun"

        fun load(context: Context): SipAccount? {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val domain = prefs.getString(KEY_DOMAIN, null) ?: return null
            val username = prefs.getString(KEY_USERNAME, null) ?: return null
            val password = prefs.getString(KEY_PASSWORD, null) ?: return null
            val port = prefs.getInt(KEY_PORT, 5060)
            val displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
            val transportStr = prefs.getString(KEY_TRANSPORT, SipTransport.UDP.name)
            val transport = try {
                SipTransport.valueOf(transportStr ?: SipTransport.UDP.name)
            } catch (e: Exception) {
                SipTransport.UDP
            }
            val stun = prefs.getString(KEY_STUN, "stun.linphone.org") ?: "stun.linphone.org"

            return SipAccount(
                domain = domain,
                port = port,
                username = username,
                password = password,
                displayName = displayName,
                transport = transport,
                stunServer = stun
            )
        }

        fun save(context: Context, account: SipAccount) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_DOMAIN, account.domain)
                .putInt(KEY_PORT, account.port)
                .putString(KEY_USERNAME, account.username)
                .putString(KEY_PASSWORD, account.password)
                .putString(KEY_DISPLAY_NAME, account.displayName)
                .putString(KEY_TRANSPORT, account.transport.name)
                .putString(KEY_STUN, account.stunServer)
                .apply()
        }

        fun clear(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }
}
