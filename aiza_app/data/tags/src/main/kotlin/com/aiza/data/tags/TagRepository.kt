package com.aiza.data.tags

import kotlinx.coroutines.flow.Flow

interface TagRepository {
    val tags: Flow<List<Tag>>
    fun tag(address: String): Flow<Tag?>
    suspend fun add(tag: Tag)
    suspend fun update(address: String, change: (Tag) -> Tag)
    /** Unpair: removes the tag and everything stored about it (FR-SET-03). */
    suspend fun remove(address: String)
}
