package com.example.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.db.AppDatabase
import com.example.db.MessageEntity
import com.example.db.MessageRepository
import com.example.network.MeshNetworkManager
import com.example.network.Message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val context: Context,
    private val meshNetworkManager: MeshNetworkManager,
    private val repository: MessageRepository
) : ViewModel() {

    init {
        createNotificationChannel()

        // Collect messages from MeshNetworkManager and save to DB
        viewModelScope.launch {
            meshNetworkManager.messageEventFlow.collect { msg ->
                val entity = MessageEntity(
                    id = msg.id,
                    senderNodeId = msg.senderNodeId,
                    senderName = msg.senderName,
                    text = msg.text,
                    isMe = msg.isMe,
                    targetNodeId = msg.targetNodeId,
                    fileUri = msg.fileUri
                )
                repository.insertMessage(entity)

                if (!msg.isMe) {
                    showNotification(msg)
                }
            }
        }


        // Handle sync requests
        viewModelScope.launch {
            meshNetworkManager.syncReqFlow.collect { (requesterNodeId, groupId) ->
                val groupMessages = repository.getMessagesForTarget(groupId)
                groupMessages.forEach { msg ->
                    meshNetworkManager.sendSyncData(
                        targetNodeId = requesterNodeId,
                        groupId = groupId,
                        originalMessageId = msg.id,
                        senderId = msg.senderNodeId,
                        senderName = msg.senderName,
                        text = msg.text,
                        fileUri = msg.fileUri
                    )
                }
            }
        }

        // Handle group invites
        viewModelScope.launch {
            meshNetworkManager.groupInviteFlow.collect { groupName ->
                val groupId = groupName
                val friendlyName = groupName.removePrefix("GROUP_")
                repository.joinGroup(groupId, friendlyName)
                meshNetworkManager.sendSyncReq(groupId)
            }
        }

        // Handle incoming delete requests
        viewModelScope.launch {
            meshNetworkManager.deleteMsgFlow.collect { msgId ->
                repository.deleteMessage(msgId)
            }
        }
    }

    val peers = meshNetworkManager.peers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val groups = repository.allGroups.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val messages = repository.allMessages.map { list ->
        list.map { entity ->
            Message(
                id = entity.id,
                senderNodeId = entity.senderNodeId,
                senderName = entity.senderName,
                text = entity.text,
                isMe = entity.isMe,
                targetNodeId = entity.targetNodeId,
                fileUri = entity.fileUri
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val myName = meshNetworkManager.myName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Node"
    )

    val myNodeId = meshNetworkManager.myNodeId

    fun startMesh() {
        meshNetworkManager.start()
    }

    fun stopMesh() {
        meshNetworkManager.stop()
    }

    fun restartScanning() {
        meshNetworkManager.restartScanning()
    }

    fun updateName(newName: String) {
        meshNetworkManager.updateName(newName)
    }

    fun sendMessage(text: String, targetNodeId: String = "GLOBAL") {
        if (text.isNotBlank()) {
            meshNetworkManager.sendMessage(text.trim(), targetNodeId)
        }
    }

    fun joinGroup(groupName: String) {
        val groupId = "GROUP_${groupName.uppercase().replace(" ", "_")}"
        viewModelScope.launch {
            repository.joinGroup(groupId, groupName)
            meshNetworkManager.sendSyncReq(groupId)
        }
    }

    fun sendFile(uri: Uri, targetNodeId: String = "GLOBAL") {
        meshNetworkManager.sendFile(uri, targetNodeId)
    }

    fun deleteMessage(msgId: String, targetNodeId: String) {
        viewModelScope.launch {
            repository.deleteMessage(msgId)
        }
        meshNetworkManager.sendDeleteMessage(msgId, targetNodeId)
    }

    fun sendInvite(groupId: String, targetNodeId: String) {
        meshNetworkManager.sendInvite(groupId, targetNodeId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Mesh Messages"
            val descriptionText = "Notifications for incoming mesh messages"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("mesh_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(msg: Message) {
        try {
            val intent = Intent(context, Class.forName("com.example.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val builder = NotificationCompat.Builder(context, "mesh_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(msg.senderName)
                .setContentText(msg.text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(msg.id.hashCode(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        meshNetworkManager.stop()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "mesh-net-db"
                ).fallbackToDestructiveMigration().build()
                val repository = MessageRepository(db.messageDao(), db.groupDao())
                
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(context.applicationContext, MeshNetworkManager(context.applicationContext), repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
