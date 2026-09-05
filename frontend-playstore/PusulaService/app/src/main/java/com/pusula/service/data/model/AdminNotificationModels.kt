package com.pusula.service.data.model

data class AdminNotificationDTO(
    val id: Long,
    val title: String,
    val message: String,
    val severity: String,
    val category: String,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val read: Boolean = false,
    val createdAt: String
)

data class NotificationUnreadCountDTO(val count: Long = 0)
