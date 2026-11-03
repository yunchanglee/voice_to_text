package com.voicetotext.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voicetotext.app.data.model.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "voice_to_text_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        val KEY_EMAIL = stringPreferencesKey("user_email")
        val KEY_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val KEY_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val KEY_FOLDER_ID = stringPreferencesKey("google_drive_folder_id")
        val KEY_LANGUAGE = stringPreferencesKey("stt_language")
        val KEY_AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
    }

    val userAccountFlow: Flow<UserAccount> = context.dataStore.data.map { prefs ->
        val email = prefs[KEY_EMAIL] ?: ""
        UserAccount(
            email = email,
            displayName = prefs[KEY_DISPLAY_NAME] ?: (if (email.isNotEmpty()) email.substringBefore("@") else "사용자"),
            photoUrl = prefs[KEY_PHOTO_URL],
            isDefaultDeviceAccount = true,
            folderId = prefs[KEY_FOLDER_ID]
        )
    }

    val folderIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_FOLDER_ID]
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "auto"
    }

    suspend fun saveLanguage(langCode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = langCode
        }
    }

    suspend fun saveUserAccount(account: UserAccount) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = account.email
            prefs[KEY_DISPLAY_NAME] = account.displayName
            if (account.photoUrl != null) prefs[KEY_PHOTO_URL] = account.photoUrl else prefs.remove(KEY_PHOTO_URL)
            if (account.folderId != null) prefs[KEY_FOLDER_ID] = account.folderId
        }
    }

    suspend fun saveFolderId(folderId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOLDER_ID] = folderId
        }
    }

    suspend fun clearAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_PHOTO_URL)
            prefs.remove(KEY_FOLDER_ID)
        }
    }
}
