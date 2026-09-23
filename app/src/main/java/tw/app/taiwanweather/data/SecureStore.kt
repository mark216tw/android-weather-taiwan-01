package tw.app.taiwanweather.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("weather_settings")

class SecureStore(private val context: Context) {
    private val cwaKey = stringPreferencesKey("cwa_api_key")
    private val moenvKey = stringPreferencesKey("moenv_api_key")
    private val favoritesKey = stringPreferencesKey("favorites")
    private val selectedKey = stringPreferencesKey("selected")
    private val displayModeKey = stringPreferencesKey("display_mode")

    suspend fun apiKeys(): Pair<String, String> {
        val values = context.dataStore.data.first()
        return decrypt(values[cwaKey]).orEmpty() to decrypt(values[moenvKey]).orEmpty()
    }

    suspend fun saveApiKeys(cwa: String, moenv: String) {
        context.dataStore.edit {
            it[cwaKey] = encrypt(cwa.trim())
            it[moenvKey] = encrypt(moenv.trim())
        }
    }

    suspend fun favorites(): List<Place> = context.dataStore.data.first()[favoritesKey]
        .orEmpty().split("|").mapNotNull(::decodePlace)

    suspend fun saveFavorites(places: List<Place>) {
        context.dataStore.edit { it[favoritesKey] = places.distinct().joinToString("|") { p -> "${p.county}::${p.township}" } }
    }

    suspend fun selected(): Place? = decodePlace(context.dataStore.data.first()[selectedKey].orEmpty())

    suspend fun saveSelected(place: Place) {
        context.dataStore.edit { it[selectedKey] = "${place.county}::${place.township}" }
    }

    suspend fun displayMode(): DisplayMode = runCatching {
        DisplayMode.valueOf(context.dataStore.data.first()[displayModeKey] ?: DisplayMode.SYSTEM.name)
    }.getOrDefault(DisplayMode.SYSTEM)

    suspend fun saveDisplayMode(mode: DisplayMode) {
        context.dataStore.edit { it[displayModeKey] = mode.name }
    }

    private fun decodePlace(raw: String): Place? {
        val parts = raw.split("::")
        return if (parts.size == 2 && TaiwanCounties.any { it.name == parts[0] }) Place(parts[0], parts[1]) else null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String?): String? = runCatching {
        if (value.isNullOrBlank()) return null
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        String(cipher.doFinal(payload.copyOfRange(12, payload.size)))
    }.getOrNull()

    private companion object { const val KEY_ALIAS = "taiwan_weather_api_keys" }
}
