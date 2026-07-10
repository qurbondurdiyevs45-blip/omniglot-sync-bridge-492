package com.omniglot.syncbridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class SyncServiceClient : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private val configStorage = ConcurrentHashMap<String, String>()
    private val PORT = 8889
    private val BUFFER_SIZE = 4096
    
    private var isRunning = false
    private lateinit var socket: DatagramSocket

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startSyncLoop()
        }
        return START_STICKY
    }

    private fun startSyncLoop() {
        serviceScope.launch {
            try {
                socket = DatagramSocket(PORT)
                socket.broadcast = true
                
                Log.i("SyncServiceClient", "Sync Bridge started on port $PORT")
                
                launch { listenForPackets() }
                launch { broadcastHeartbeat() }
                
            } catch (e: Exception) {
                Log.e("SyncServiceClient", "Failed to initialize socket", e)
            }
        }
    }

    private suspend fun listenForPackets() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                
                val data = String(packet.data, 0, packet.length)
                handleIncomingSync(data)
            } catch (e: Exception) {
                Log.e("SyncServiceClient", "Error receiving packet", e)
            }
        }
    }

    private fun handleIncomingSync(payload: String) {
        try {
            val json = JSONObject(payload)
            val type = json.optString("type")
            
            if (type == "SYNC_UPDATE") {
                val data = json.getJSONObject("payload")
                val keys = data.keys()
                var updated = false
                
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = data.getString(key)
                    if (configStorage[key] != value) {
                        configStorage[key] = value
                        updated = true
                    }
                }
                
                if (updated) {
                    notifyApplicationOfChange()
                }
            }
        } catch (e: Exception) {
            Log.w("SyncServiceClient", "Malformed sync packet received")
        }
    }

    private suspend fun broadcastHeartbeat() = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val heartbeat = JSONObject().apply {
                    put("type", "HEARTBEAT")
                    put("deviceId", android.os.Build.MODEL)
                    put("timestamp", System.currentTimeMillis())
                }
                
                val bytes = heartbeat.toString().toByteArray()
                val address = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(bytes, bytes.size, address, PORT)
                
                socket.send(packet)
                delay(5000) // Mesh synchronization interval
            } catch (e: Exception) {
                Log.e("SyncServiceClient", "Heartbeat failed", e)
                delay(10000)
            }
        }
    }

    private fun notifyApplicationOfChange() {
        val intent = Intent("com.omniglot.syncbridge.CONFIG_UPDATED")
        val bundle = android.os.Bundle()
        configStorage.forEach { (k, v) -> bundle.putString(k, v) }
        intent.putExtras(bundle)
        sendBroadcast(intent)
        Log.d("SyncServiceClient", "Broadcasted sync update to application")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        if (::socket.isInitialized) {
            socket.close()
        }
    }
}