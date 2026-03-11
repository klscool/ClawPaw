package com.example.clawpaw.ssh

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "clawpaw_ssh"
private const val KEY_HOST = "host"
private const val KEY_PORT = "port"
private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"
private const val KEY_PROXY_HOST = "proxy_host"
private const val KEY_PROXY_PORT = "proxy_port"
private const val KEY_MAPPINGS = "port_mappings"
private const val KEY_REVERSE_MAPPINGS = "reverse_port_mappings"
private const val KEY_WANTED_SSH_CONNECTED = "wanted_ssh_connected"

object SshPrefs {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getWantedSshConnected(): Boolean = prefs?.getBoolean(KEY_WANTED_SSH_CONNECTED, false) ?: false

    fun setWantedSshConnected(wanted: Boolean) {
        prefs?.edit()?.putBoolean(KEY_WANTED_SSH_CONNECTED, wanted)?.apply()
    }

    fun getConfig(): SshTunnelConfig {
        val p = prefs ?: return SshTunnelConfig()
        return SshTunnelConfig(
            host = p.getString(KEY_HOST, "") ?: "",
            port = p.getInt(KEY_PORT, 22),
            username = p.getString(KEY_USERNAME, "") ?: "",
            password = p.getString(KEY_PASSWORD, "") ?: "",
            proxyHost = p.getString(KEY_PROXY_HOST, "127.0.0.1") ?: "127.0.0.1",
            proxyPort = p.getInt(KEY_PROXY_PORT, 0)
        )
    }

    fun saveConfig(config: SshTunnelConfig) {
        prefs?.edit()?.apply {
            putString(KEY_HOST, config.host)
            putInt(KEY_PORT, config.port)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, config.password)
            putString(KEY_PROXY_HOST, config.proxyHost)
            putInt(KEY_PROXY_PORT, config.proxyPort)
            apply()
        }
    }

    fun getPortMappings(): List<PortMapping> {
        val json = prefs?.getString(KEY_MAPPINGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                PortMapping(
                    localPort = o.getInt("localPort"),
                    remoteHost = o.getString("remoteHost"),
                    remotePort = o.getInt("remotePort")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePortMappings(mappings: List<PortMapping>) {
        val arr = JSONArray()
        mappings.forEach { m ->
            arr.put(JSONObject().apply {
                put("localPort", m.localPort)
                put("remoteHost", m.remoteHost)
                put("remotePort", m.remotePort)
            })
        }
        prefs?.edit()?.putString(KEY_MAPPINGS, arr.toString())?.apply()
    }

    fun getReversePortMappings(): List<ReversePortMapping> {
        val json = prefs?.getString(KEY_REVERSE_MAPPINGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ReversePortMapping(
                    remotePort = o.getInt("remotePort"),
                    localHost = o.getString("localHost"),
                    localPort = o.getInt("localPort")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveReversePortMappings(mappings: List<ReversePortMapping>) {
        val arr = JSONArray()
        mappings.forEach { m ->
            arr.put(JSONObject().apply {
                put("remotePort", m.remotePort)
                put("localHost", m.localHost)
                put("localPort", m.localPort)
            })
        }
        prefs?.edit()?.putString(KEY_REVERSE_MAPPINGS, arr.toString())?.apply()
    }
}
