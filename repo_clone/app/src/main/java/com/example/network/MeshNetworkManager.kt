package com.example.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import android.os.Environment

data class Peer(val endpointId: String, val nodeId: String, val name: String, val isConnected: Boolean)
data class Message(val id: String, val senderNodeId: String, val senderName: String, val text: String, val isMe: Boolean, val targetNodeId: String, val fileUri: String? = null)

data class FilePayloadMeta(val messageId: String, val senderNodeId: String, val senderName: String, val targetNodeId: String, val fileName: String)

class MeshNetworkManager(private val context: Context) {
    private val TAG = "MeshNetworkManager"
    private val SERVICE_ID = "com.aistudio.meshnet"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val connectionsClient = Nearby.getConnectionsClient(context)
    
    // Identity - Node ID persists across restarts
    val myNodeId = UUID.randomUUID().toString().take(8)
    private val prefs = context.getSharedPreferences("mesh_prefs", Context.MODE_PRIVATE)
    private val _myName = MutableStateFlow(prefs.getString("my_name", "Node-${(100..999).random()}") ?: "")
    val myName: StateFlow<String> = _myName.asStateFlow()
    
    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()
    
    private val _messageEventFlow = MutableSharedFlow<Message>(replay = 0, extraBufferCapacity = 100)
    val messageEventFlow: SharedFlow<Message> = _messageEventFlow.asSharedFlow()

    private val _syncReqFlow = MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 100)
    val syncReqFlow: SharedFlow<Pair<String, String>> = _syncReqFlow.asSharedFlow()
    
    private val _groupInviteFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 100)
    val groupInviteFlow: SharedFlow<String> = _groupInviteFlow.asSharedFlow()

    private val _deleteMsgFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 100)
    val deleteMsgFlow: SharedFlow<String> = _deleteMsgFlow.asSharedFlow()

    private val receivedMessageIds = mutableSetOf<String>()
    
    // For file tracking
    private val incomingFiles = mutableMapOf<Long, FilePayloadMeta>()
    private val pendingFilePayloads = mutableMapOf<Long, Payload>()

    fun updateName(newName: String) {
        if (newName.isNotBlank() && _myName.value != newName.trim()) {
            _myName.value = newName.trim()
            prefs.edit().putString("my_name", _myName.value).apply()
            
            // Broadcast name change
            val messageId = UUID.randomUUID().toString()
            val rawData = "$messageId|$myNodeId|${_myName.value}|GLOBAL|RENAME|${_myName.value}"
            val payload = Payload.fromBytes(CryptoManager.encrypt(rawData).toByteArray(Charsets.UTF_8))
            sendPayloadToTargets(payload, null, null)
            
            // Update advertising name
            connectionsClient.stopAdvertising()
            startAdvertising()
        }
    }

    private fun restart() {
        stop()
        start()
    }

    fun start() {
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        _peers.update { emptyMap() }
    }

    fun restartScanning() {
        Log.d(TAG, "Restarting scanning manually")
        connectionsClient.stopDiscovery()
        connectionsClient.stopAdvertising()
        _peers.update { emptyMap() }
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val endpointName = "$myNodeId|${_myName.value}"
        connectionsClient.startAdvertising(
            endpointName, SERVICE_ID, connectionLifecycleCallback, options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed", e)
        }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
        }
    }

    fun sendMessage(text: String, targetNodeId: String = "GLOBAL") {
        val messageId = UUID.randomUUID().toString()
        val rawData = "$messageId|$myNodeId|${_myName.value}|$targetNodeId|TEXT|$text"
        val encryptedData = CryptoManager.encrypt(rawData)
        val payload = Payload.fromBytes(encryptedData.toByteArray(Charsets.UTF_8))
        
        receivedMessageIds.add(messageId)
        val msg = Message(messageId, myNodeId, _myName.value, text, true, targetNodeId)
        _messageEventFlow.tryEmit(msg)
        
        sendPayloadToTargets(payload, null, targetNodeId)
    }

    fun sendInvite(groupId: String, targetNodeId: String) {
        val messageId = UUID.randomUUID().toString()
        val rawData = "$messageId|$myNodeId|${_myName.value}|$targetNodeId|INVITE|$groupId"
        val encryptedData = CryptoManager.encrypt(rawData)
        val payload = Payload.fromBytes(encryptedData.toByteArray(Charsets.UTF_8))
        
        receivedMessageIds.add(messageId)
        sendPayloadToTargets(payload, null, targetNodeId)
    }

    fun sendDeleteMessage(messageIdToDelete: String, targetNodeId: String) {
        val messageId = UUID.randomUUID().toString()
        val rawData = "$messageId|$myNodeId|${_myName.value}|$targetNodeId|DELETE_MSG|$messageIdToDelete"
        val encryptedData = CryptoManager.encrypt(rawData)
        val payload = Payload.fromBytes(encryptedData.toByteArray(Charsets.UTF_8))
        
        receivedMessageIds.add(messageId)
        sendPayloadToTargets(payload, null, targetNodeId)
    }

    fun sendSyncReq(groupId: String) {
        val messageId = UUID.randomUUID().toString()
        val rawData = "$messageId|$myNodeId|${_myName.value}|GLOBAL|SYNC_REQ|$groupId"
        val encryptedData = CryptoManager.encrypt(rawData)
        val payload = Payload.fromBytes(encryptedData.toByteArray(Charsets.UTF_8))
        receivedMessageIds.add(messageId)
        sendPayloadToTargets(payload, null, null)
    }

    fun sendSyncData(targetNodeId: String, groupId: String, originalMessageId: String, senderId: String, senderName: String, text: String, fileUri: String?) {
        val fUri = fileUri ?: "null"
        val syncContent = "$senderId;;$senderName;;$text;;$fUri"
        val rawData = "$originalMessageId|$myNodeId|${_myName.value}|$groupId|SYNC_DATA|$syncContent"
        val encryptedData = CryptoManager.encrypt(rawData)
        val payload = Payload.fromBytes(encryptedData.toByteArray(Charsets.UTF_8))
        receivedMessageIds.add(originalMessageId) // add original
        sendPayloadToTargets(payload, null, targetNodeId)
    }

    fun sendFile(uri: Uri, targetNodeId: String = "GLOBAL") {
        val contentResolver = context.contentResolver
        var fileName = "unknown_file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val tempEncFile = java.io.File(cacheDir, "enc_payload_${UUID.randomUUID()}")
                
                contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempEncFile).use { output ->
                        CryptoManager.encryptStream(input, output)
                    }
                }

                val pfd = android.os.ParcelFileDescriptor.open(tempEncFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val filePayload = Payload.fromFile(pfd)
                
                val messageId = UUID.randomUUID().toString()
                val metaData = "$messageId|$myNodeId|${_myName.value}|$targetNodeId|FILE_META|${filePayload.id}|$fileName"
                val metaPayload = Payload.fromBytes(CryptoManager.encrypt(metaData).toByteArray(Charsets.UTF_8))
                
                receivedMessageIds.add(messageId)
                val msg = Message(messageId, myNodeId, _myName.value, "📁 Sent file: $fileName", true, targetNodeId, uri.toString())
                _messageEventFlow.tryEmit(msg)
                
                sendPayloadToTargets(metaPayload, null, targetNodeId)
                sendPayloadToTargets(filePayload, null, targetNodeId)
            } catch (e: Exception) {
                Log.e(TAG, "File send error", e)
            }
        }
    }
    
    private fun sendPayloadToTargets(payload: Payload, excludeEndpointId: String?, specificTargetNodeId: String?) {
        val targets = _peers.value.values
            .filter { it.isConnected && it.endpointId != excludeEndpointId }

        val finalTargetEndpoints = if (specificTargetNodeId != null && specificTargetNodeId != "GLOBAL" && !specificTargetNodeId.startsWith("GROUP_")) {
            // Unicast: we only send to the known endpoint if directly connected. 
            // In a better mesh we would route, but to save bandwidth, we try to send directly if we can
            val directPeer = targets.find { it.nodeId == specificTargetNodeId }
            if (directPeer != null) listOf(directPeer.endpointId) else targets.map { it.endpointId }
        } else {
            // Broadcast to all
            targets.map { it.endpointId }
        }

        if (finalTargetEndpoints.isNotEmpty()) {
            connectionsClient.sendPayload(finalTargetEndpoints, payload)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                pendingFilePayloads[payload.id] = payload
                return
            }
            
            payload.asBytes()?.let { bytes ->
                GlobalScope.launch(Dispatchers.IO) {
                    val encryptedData = String(bytes, Charsets.UTF_8)
                    val rawData = CryptoManager.decrypt(encryptedData)
                    val parts = rawData.split("|", limit = 6)
                    if (parts.size == 6) {
                        val messageId = parts[0]
                        val senderNodeId = parts[1]
                        val senderName = parts[2]
                        val targetNodeId = parts[3]
                        val msgType = parts[4]
                        
                        if (receivedMessageIds.contains(messageId)) {
                            return@launch // Loop prevention in mesh
                        }
                        receivedMessageIds.add(messageId)
                        
                        if (msgType == "RENAME") {
                            val newName = parts[5]
                            _peers.update { current ->
                                val newMap = current.toMutableMap()
                                val peerEntry = newMap.entries.find { it.value.nodeId == senderNodeId }
                                if (peerEntry != null) {
                                    newMap[peerEntry.key] = peerEntry.value.copy(name = newName)
                                }
                                newMap
                            }
                        } else if (msgType == "INVITE") {
                            val groupName = parts[5]
                            _groupInviteFlow.tryEmit(groupName)
                            return@launch
                        } else if (msgType == "SYNC_REQ") {
                            val groupId = parts[5]
                            _syncReqFlow.tryEmit(Pair(senderNodeId, groupId))
                            return@launch
                        } else if (msgType == "SYNC_DATA") {
                            try {
                                val msgParts = parts[5].split(";;", limit = 4)
                                if (msgParts.size == 4) {
                                    val sId = msgParts[0]
                                    val sName = msgParts[1]
                                    val txt = msgParts[2]
                                    val fUri = if (msgParts[3] == "null") null else msgParts[3]
                                    val msg = Message(messageId, sId, sName, txt, false, targetNodeId, fUri)
                                    _messageEventFlow.tryEmit(msg)
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                            return@launch
                        } else if (msgType == "DELETE_MSG") {
                            val msgIdToDelete = parts[5]
                            _deleteMsgFlow.tryEmit(msgIdToDelete)
                            if (targetNodeId != myNodeId) {
                                sendPayloadToTargets(Payload.fromBytes(CryptoManager.encrypt(rawData).toByteArray(Charsets.UTF_8)), endpointId, targetNodeId)
                            }
                            return@launch
                        }
                        
                        val text = if (msgType == "FILE_META") {
                            val metaParts = parts[5].split("|", limit = 2)
                            val filePayloadId = metaParts[0].toLongOrNull() ?: 0L
                            val fileName = metaParts.getOrNull(1) ?: "unknown"
                            incomingFiles[filePayloadId] = FilePayloadMeta(messageId, senderNodeId, senderName, targetNodeId, fileName)
                            "⏳ Receiving file: $fileName..."
                        } else if (msgType == "RENAME") {
                            "🔁 Changed name to ${parts[5]}"
                        } else {
                            parts[5]
                        }
                        
                        // Show in UI if it's for me globally, direct, or group chat
                        if (targetNodeId == "GLOBAL" || targetNodeId == myNodeId || senderNodeId == myNodeId || targetNodeId.startsWith("GROUP_")) {
                            val msg = Message(messageId, senderNodeId, senderName, text, false, targetNodeId)
                            _messageEventFlow.tryEmit(msg)
                        }
                        
                        // Relay to other connected peers to form a true mesh
                        if (targetNodeId != myNodeId && msgType != "FILE_META") {
                            sendPayloadToTargets(Payload.fromBytes(CryptoManager.encrypt(rawData).toByteArray(Charsets.UTF_8)), endpointId, targetNodeId)
                        }
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payload = pendingFilePayloads[update.payloadId]
                val meta = incomingFiles[update.payloadId]
                
                if (payload != null && meta != null) {
                    var providedUriStr: String? = null
                    try {
                        val uri = payload.asFile()?.asUri()
                        if (uri != null) {
                            val cacheDir = context.cacheDir
                            val tempEncFile = java.io.File(cacheDir, "relay_enc_${update.payloadId}")
                            
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(tempEncFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            val isForMe = meta.targetNodeId == "GLOBAL" || meta.targetNodeId == myNodeId || meta.targetNodeId.startsWith("GROUP_")
                            if (isForMe) {
                                val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                if (downloadDir != null && !downloadDir.exists()) downloadDir.mkdirs()
                                
                                val destFile = java.io.File(downloadDir, meta.fileName)
                                java.io.FileInputStream(tempEncFile).use { input ->
                                    java.io.FileOutputStream(destFile).use { output ->
                                        CryptoManager.decryptStream(input, output)
                                    }
                                }
                                
                                val providerUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    destFile
                                )
                                providedUriStr = providerUri.toString()
                                
                                val completeMsg = Message(meta.messageId, meta.senderNodeId, meta.senderName, "📁 Received file: ${meta.fileName}", false, meta.targetNodeId, providedUriStr)
                                _messageEventFlow.tryEmit(completeMsg)
                            }
                            
                            if (meta.targetNodeId != myNodeId) {
                                // We must forward it
                                val pfd = android.os.ParcelFileDescriptor.open(tempEncFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                val filePayloadToForward = Payload.fromFile(pfd)
                                val newMetaData = "${meta.messageId}|${meta.senderNodeId}|${meta.senderName}|${meta.targetNodeId}|FILE_META|${filePayloadToForward.id}|${meta.fileName}"
                                val metaPayload = Payload.fromBytes(CryptoManager.encrypt(newMetaData).toByteArray(Charsets.UTF_8))
                                
                                sendPayloadToTargets(metaPayload, endpointId, meta.targetNodeId)
                                sendPayloadToTargets(filePayloadToForward, endpointId, meta.targetNodeId)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    pendingFilePayloads.remove(update.payloadId)
                    incomingFiles.remove(update.payloadId)
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val parts = info.endpointName.split("|", limit = 2)
            val nodeId = if (parts.size == 2) parts[0] else endpointId
            val nodeName = if (parts.size == 2) parts[1] else info.endpointName
            
            Log.d(TAG, "Connection initiated: $nodeName")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            _peers.update { current ->
                val newMap = current.toMutableMap()
                newMap[endpointId] = Peer(endpointId, nodeId, nodeName, false)
                newMap
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                Log.d(TAG, "Connected to: $endpointId")
                _peers.update { current ->
                    val newMap = current.toMutableMap()
                    val peer = newMap[endpointId]
                    if (peer != null) {
                        newMap[endpointId] = peer.copy(isConnected = true)
                    }
                    newMap
                }
            } else {
                Log.d(TAG, "Connection failed to: $endpointId")
                _peers.update { current ->
                    val newMap = current.toMutableMap()
                    newMap.remove(endpointId)
                    newMap
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            _peers.update { current ->
                val newMap = current.toMutableMap()
                newMap.remove(endpointId)
                newMap
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: ${info.endpointName}")
            val endpointName = "$myNodeId|${_myName.value}"
            connectionsClient.requestConnection(endpointName, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "Connection requested to ${info.endpointName}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Connection request failed", e)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }
}
