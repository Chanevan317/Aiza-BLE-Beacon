package com.aiza.data.tags

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.tagStore by preferencesDataStore(name = "tags")

/** Real paired tags, persisted on the phone only (C-5). */
class DataStoreTagRepository(private val context: Context) : TagRepository {
    private val key = stringPreferencesKey("tags_json")
    private val json = Json { ignoreUnknownKeys = true }

    override val tags: Flow<List<Tag>> = context.tagStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<List<Tag>>(it) }.getOrNull() } ?: emptyList()
    }

    override fun tag(address: String): Flow<Tag?> = tags.map { list -> list.find { it.address == address } }

    override suspend fun add(tag: Tag) = edit { list -> list.filterNot { it.address == tag.address } + tag }

    override suspend fun update(address: String, change: (Tag) -> Tag) =
        edit { list -> list.map { if (it.address == address) change(it) else it } }

    override suspend fun remove(address: String) = edit { list -> list.filterNot { it.address == address } }

    private suspend fun edit(transform: (List<Tag>) -> List<Tag>) {
        context.tagStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<Tag>>(it) }.getOrNull() } ?: emptyList()
            prefs[key] = json.encodeToString(transform(current))
        }
    }
}
