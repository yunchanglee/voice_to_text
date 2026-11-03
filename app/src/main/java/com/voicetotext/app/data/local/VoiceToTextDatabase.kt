package com.voicetotext.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.voicetotext.app.data.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = try {
        SyncStatus.valueOf(value)
    } catch (e: Exception) {
        SyncStatus.PENDING
    }
}

@Database(
    entities = [DocumentEntity::class, TranscriptEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VoiceToTextDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceToTextDatabase? = null

        fun getInstance(context: Context): VoiceToTextDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceToTextDatabase::class.java,
                    "voice_to_text_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
