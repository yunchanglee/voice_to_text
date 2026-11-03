package com.voicetotext.app

import android.app.Application
import com.voicetotext.app.data.local.LocalFileManager
import com.voicetotext.app.data.local.PreferencesManager
import com.voicetotext.app.data.local.VoiceToTextDatabase
import com.voicetotext.app.data.repository.VoiceToTextRepository
import com.voicetotext.app.data.translation.TranslationManager

class VoiceToTextApplication : Application() {

    lateinit var database: VoiceToTextDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var localFileManager: LocalFileManager
        private set

    lateinit var translationManager: TranslationManager
        private set

    lateinit var repository: VoiceToTextRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = VoiceToTextDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        localFileManager = LocalFileManager(this)
        translationManager = TranslationManager(this)
        repository = VoiceToTextRepository(this, database, preferencesManager, localFileManager, translationManager)
    }

    companion object {
        lateinit var instance: VoiceToTextApplication
            private set
    }
}
