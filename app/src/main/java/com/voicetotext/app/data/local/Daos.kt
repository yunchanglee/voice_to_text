package com.voicetotext.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.voicetotext.app.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' OR summaryPreview LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("UPDATE documents SET durationSeconds = :duration, segmentCount = :segments, charCount = :chars, summaryPreview = :preview, localFilePath = :filePath, fileSizeBytes = :fileSize WHERE id = :id")
    suspend fun updateStats(id: String, duration: Long, segments: Int, chars: Int, preview: String, filePath: String?, fileSize: Long)

    @Query("UPDATE documents SET durationSeconds = :duration, segmentCount = :segments, charCount = :chars, summaryPreview = :preview WHERE id = :id")
    suspend fun updateStatsBasic(id: String, duration: Long, segments: Int, chars: Int, preview: String)

    @Query("UPDATE documents SET language = :language WHERE id = :id")
    suspend fun updateLanguage(id: String, language: String)

    @Query("UPDATE documents SET title = :newTitle, localFilePath = :newFilePath WHERE id = :id")
    suspend fun updateTitleAndPath(id: String, newTitle: String, newFilePath: String?)

    @Query("UPDATE documents SET title = :newTitle, localFilePath = :newFilePath, translatedFilePath = :newTransPath WHERE id = :id")
    suspend fun updateTitleAndPaths(id: String, newTitle: String, newFilePath: String?, newTransPath: String?)

    @Query("UPDATE documents SET translatedText = :transText, targetLanguage = :targetLang, translatedFilePath = :filePath WHERE id = :id")
    suspend fun updateTranslation(id: String, transText: String, targetLang: String, filePath: String?)

    @Query("UPDATE documents SET syncStatus = :status, googleDocsFileId = :fileId WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus, fileId: String?)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE documentId = :docId ORDER BY timestampMs ASC")
    fun getTranscriptsForDocument(docId: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE documentId = :docId ORDER BY timestampMs ASC")
    suspend fun getTranscriptsListForDocument(docId: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE documentId = :docId AND isSynced = 0 ORDER BY timestampMs ASC")
    suspend fun getUnsyncedTranscripts(docId: String): List<TranscriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity): Long

    @Query("UPDATE transcripts SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("UPDATE transcripts SET translatedText = :translatedText WHERE id = :id")
    suspend fun updateTranscriptTranslation(id: Long, translatedText: String)

    @Query("DELETE FROM transcripts")
    suspend fun deleteAllTranscripts()
}
