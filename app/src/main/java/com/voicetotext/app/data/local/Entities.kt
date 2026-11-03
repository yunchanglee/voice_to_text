package com.voicetotext.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.voicetotext.app.data.model.SyncStatus

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String, // UUID
    val title: String,
    val googleDocsFileId: String? = null,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val language: String = "ko-KR",
    val createdAt: Long,
    val durationSeconds: Long,
    val segmentCount: Int,
    val charCount: Int,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val summaryPreview: String,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
    val translatedFilePath: String? = null
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: String,
    val timestampMs: Long,
    val formattedTime: String,
    val text: String,
    val isSynced: Boolean,
    val translatedText: String? = null
)
