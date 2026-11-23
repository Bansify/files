package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SafeFolderRecord
import com.example.model.SortOption
import com.example.model.ThemePreference
import com.example.model.TrashRecord
import com.example.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.bansifyDataStore: DataStore<Preferences> by preferencesDataStore(name = "bansify_files_prefs")

data class UserSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val showHiddenFiles: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortOption: SortOption = SortOption.NAME_ASC,
    val useDynamicColor: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorites_paths")
        val TRASH_RECORDS = stringPreferencesKey("trash_records_json")
        val SAFE_RECORDS = stringPreferencesKey("safe_records_json")
        val SAFE_PIN_HASH = stringPreferencesKey("safe_pin_hash")
        val SAFE_PIN_SALT = stringPreferencesKey("safe_pin_salt")
        val THEME = stringPreferencesKey("theme_preference")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    val settingsFlow: Flow<UserSettings> = context.bansifyDataStore.data.map { prefs ->
        UserSettings(
            themePreference = prefs[Keys.THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            viewMode = prefs[Keys.VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() }
                ?: ViewMode.LIST,
            sortOption = prefs[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
                ?: SortOption.NAME_ASC,
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false
        )
    }

    val favoritesFlow: Flow<Set<String>> = context.bansifyDataStore.data.map { prefs ->
        prefs[Keys.FAVORITES] ?: emptySet()
    }

    val trashRecordsFlow: Flow<List<TrashRecord>> = context.bansifyDataStore.data.map { prefs ->
        parseTrashRecords(prefs[Keys.TRASH_RECORDS])
    }

    val safeRecordsFlow: Flow<List<SafeFolderRecord>> = context.bansifyDataStore.data.map { prefs ->
        parseSafeRecords(prefs[Keys.SAFE_RECORDS])
    }

    val hasSafePinFlow: Flow<Boolean> = context.bansifyDataStore.data.map { prefs ->
        !prefs[Keys.SAFE_PIN_HASH].isNullOrBlank()
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.bansifyDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.bansifyDataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.bansifyDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setSortOption(option: SortOption) {
        context.bansifyDataStore.edit { it[Keys.SORT_OPTION] = option.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.bansifyDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun toggleFavorite(path: String): Boolean {
        var added = false
        context.bansifyDataStore.edit { prefs ->
            val current = (prefs[Keys.FAVORITES] ?: emptySet()).toMutableSet()
            if (current.contains(path)) {
                current.remove(path)
                added = false
            } else {
                current.add(path)
                added = true
            }
            prefs[Keys.FAVORITES] = current
        }
        return added
    }

    suspend fun removeFavorite(path: String) {
        context.bansifyDataStore.edit { prefs ->
            val current = (prefs[Keys.FAVORITES] ?: emptySet()).toMutableSet()
            if (current.remove(path)) {
                prefs[Keys.FAVORITES] = current
            }
        }
    }

    suspend fun updateFavoritePath(oldPath: String, newPath: String) {
        context.bansifyDataStore.edit { prefs ->
            val current = (prefs[Keys.FAVORITES] ?: emptySet()).toMutableSet()
            if (current.remove(oldPath)) {
                current.add(newPath)
                prefs[Keys.FAVORITES] = current
            }
        }
    }

    suspend fun saveTrashRecords(records: List<TrashRecord>) {
        val array = JSONArray()
        records.forEach { rec ->
            array.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("trashFileName", rec.trashFileName)
                    put("originalPath", rec.originalPath)
                    put("originalName", rec.originalName)
                    put("sizeBytes", rec.sizeBytes)
                    put("deletedAt", rec.deletedAt)
                    put("isDirectory", rec.isDirectory)
                }
            )
        }
        context.bansifyDataStore.edit { it[Keys.TRASH_RECORDS] = array.toString() }
    }

    suspend fun getTrashRecords(): List<TrashRecord> = trashRecordsFlow.first()

    suspend fun saveSafeRecords(records: List<SafeFolderRecord>) {
        val array = JSONArray()
        records.forEach { rec ->
            array.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("vaultFileName", rec.vaultFileName)
                    put("originalPath", rec.originalPath)
                    put("originalName", rec.originalName)
                    put("sizeBytes", rec.sizeBytes)
                    put("movedAt", rec.movedAt)
                    put("isDirectory", rec.isDirectory)
                }
            )
        }
        context.bansifyDataStore.edit { it[Keys.SAFE_RECORDS] = array.toString() }
    }

    suspend fun getSafeRecords(): List<SafeFolderRecord> = safeRecordsFlow.first()

    suspend fun setSafePin(pin: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val hashHex = hashPin(pin, saltHex)
        context.bansifyDataStore.edit { prefs ->
            prefs[Keys.SAFE_PIN_SALT] = saltHex
            prefs[Keys.SAFE_PIN_HASH] = hashHex
        }
    }

    suspend fun verifySafePin(pin: String): Boolean {
        val prefs = context.bansifyDataStore.data.first()
        val storedHash = prefs[Keys.SAFE_PIN_HASH] ?: return false
        val salt = prefs[Keys.SAFE_PIN_SALT] ?: return false
        return hashPin(pin, salt) == storedHash
    }

    suspend fun clearSafePin() {
        context.bansifyDataStore.edit { prefs ->
            prefs.remove(Keys.SAFE_PIN_HASH)
            prefs.remove(Keys.SAFE_PIN_SALT)
        }
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin:bansify_vault".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseTrashRecords(json: String?): List<TrashRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        TrashRecord(
                            id = obj.getString("id"),
                            trashFileName = obj.getString("trashFileName"),
                            originalPath = obj.getString("originalPath"),
                            originalName = obj.getString("originalName"),
                            sizeBytes = obj.getLong("sizeBytes"),
                            deletedAt = obj.getLong("deletedAt"),
                            isDirectory = obj.getBoolean("isDirectory")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseSafeRecords(json: String?): List<SafeFolderRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        SafeFolderRecord(
                            id = obj.getString("id"),
                            vaultFileName = obj.getString("vaultFileName"),
                            originalPath = obj.getString("originalPath"),
                            originalName = obj.getString("originalName"),
                            sizeBytes = obj.getLong("sizeBytes"),
                            movedAt = obj.getLong("movedAt"),
                            isDirectory = obj.getBoolean("isDirectory")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
