package com.aiza.data.tags

import kotlinx.coroutines.flow.Flow

/** A paired tag (FR-SET-02, FR-STA-01..03). */
data class Tag(
    val address: String,
    val name: String,
    val icon: String,
    val batteryPercent: Int?,
    val lastSeenEpochMs: Long?,
    val lastSeenLat: Double?,
    val lastSeenLon: Double?,
    val leftBehindAlert: Boolean,
)

/** TODO: back with DataStore; unpair must delete all stored data (FR-SET-03). */
interface TagRepository {
    val tags: Flow<List<Tag>>
    suspend fun add(tag: Tag)
    suspend fun update(tag: Tag)
    suspend fun remove(address: String)
}
