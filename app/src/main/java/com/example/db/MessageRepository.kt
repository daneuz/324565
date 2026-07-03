package com.example.db

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao, private val groupDao: GroupDao) {
    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val allGroups: Flow<List<GroupEntity>> = groupDao.getAllGroups()

    suspend fun getMessagesForTarget(targetNodeId: String): List<MessageEntity> {
        return messageDao.getMessagesForTarget(targetNodeId)
    }

    suspend fun getPrivateMessages(peer1: String, peer2: String): List<MessageEntity> {
        return messageDao.getPrivateMessages(peer1, peer2)
    }

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    suspend fun deleteMessage(msgId: String) {
        messageDao.deleteMessage(msgId)
    }

    suspend fun joinGroup(groupId: String, groupName: String) {
        groupDao.insertGroup(GroupEntity(groupId, groupName))
    }
}
