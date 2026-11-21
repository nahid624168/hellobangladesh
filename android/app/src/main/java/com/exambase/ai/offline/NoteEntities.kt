package com.exambase.ai.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_notes")
data class OfflineNoteEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val title: String,
    val body: String,
    val color: String?,
    val pinned: Boolean,
    val favorite: Boolean,
    val tagsCsv: String,
    val serverVersion: Int,
    /** Version we last successfully reconciled with the server. */
    val baseVersion: Int,
    val localDirty: Boolean,
    val deleted: Boolean,
    val conflict: Boolean,
    val updatedAt: String,
    val createdAt: String,
)

@Entity(tableName = "offline_folders")
data class OfflineFolderEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val color: String?,
    val position: Int,
    val deleted: Boolean,
    val updatedAt: String,
    val localDirty: Boolean,
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
