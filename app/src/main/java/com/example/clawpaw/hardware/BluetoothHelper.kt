package com.example.clawpaw.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * 蓝牙：获取已配对设备列表（需 BLUETOOTH_CONNECT）。
 */
object BluetoothHelper {

    @SuppressLint("MissingPermission")
    fun getBondedDevices(context: Context): JSONArray {
        val arr = JSONArray()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return arr
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return arr
        }
        adapter.bondedDevices?.forEach { device ->
            arr.put(JSONObject().apply {
                put("name", device.name ?: "")
                put("address", device.address)
            })
        }
        return arr
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.isEnabled
    }
}
