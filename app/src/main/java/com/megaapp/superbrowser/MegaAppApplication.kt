package com.megaapp.superbrowser

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.aead.AeadKeyTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MegaAppApplication : Application() {

    companion object {
        @Suppress("UNUSED_PARAMETER")
        val DATA_STORE_NAME = "megaapp_preferences"
        val ENCRYPTED_DATA_STORE_NAME = "megaapp_encrypted_preferences"
        const val KEY_PROVIDER_KEYS = "provider_keys"
        const val KEY_SETTINGS = "settings"
        const val KEY_MCP_CONFIG = "mcp_config"
    }

    val scope = CoroutineScope(Dispatchers.IO)
    val dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
    val encryptedDataStore: DataStore<Preferences> by preferencesDataStore(name = ENCRYPTED_DATA_STORE_NAME)

    private var keysetManager: AndroidKeysetManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("MegaApp", "Application started")
        initEncryption()
    }

    private fun initEncryption() {
        scope.launch {
            try {
                keysetManager = AndroidKeysetManager.Builder()
                    .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
                    .withSharedPref(this@MegaAppApplication, "megaapp_keyset")
                    .build()
                Log.d("MegaApp", "Encryption initialized")
            } catch (e: Exception) {
                Log.e("MegaApp", "Encryption init failed", e)
            }
        }
    }

    fun getKeysetManager(): AndroidKeysetManager? = keysetManager
}