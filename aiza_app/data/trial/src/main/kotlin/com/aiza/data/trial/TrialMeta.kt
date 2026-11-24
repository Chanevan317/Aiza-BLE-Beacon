package com.aiza.data.trial

import kotlinx.serialization.Serializable

/** Line-of-sight condition between start point and tag (SRS §6.1). */
@Serializable
enum class LineOfSight(val label: String) {
    CLEAR("Clear"), WALL("Wall"), FURNITURE("Furniture"), BAG_OR_DRAWER("Bag / drawer")
}

@Serializable
enum class SearchStyle(val label: String) { WALKING("Walking"), WALKING_AND_SPIN("Walking + spin") }

@Serializable
enum class TrialOutcome(val label: String) { RUNNING("Running"), FOUND("Found"), GAVE_UP("Not found") }

/**
 * Everything about one trial except the samples (FR-TRL-01/02/05). No names: volunteers are codes (NFR-PRV-01).
 * Times: *EpochMs are wall clock for humans; *ElapsedNs share the log's clock (elapsedRealtimeNanos).
 */
@Serializable
data class TrialMeta(
    val trialId: String,
    val volunteerCode: String,
    val consentEpochMs: Long,
    // Setup
    val tagAddress: String,
    val tagSerial: String,
    val tagName: String,
    val place: String,
    val room: String,
    /** Ground truth: straight-line distance from the start point to the tag, measured. */
    val tagDistanceM: Float,
    /** Ground truth: compass bearing from the start point to the tag, degrees from magnetic north. */
    val tagBearingDeg: Float?,
    val lineOfSight: LineOfSight,
    val searchStyle: SearchStyle,
    val wedgeShown: Boolean,
    val notes: String = "",
    // Device
    val phoneModel: String,
    val androidVersion: String,
    val appVersion: String,
    /** True when the signal came from the built-in simulator, not a real tag. */
    val simulated: Boolean,
    // Run
    val startEpochMs: Long,
    val startElapsedNs: Long,
    val endElapsedNs: Long? = null,
    val outcome: TrialOutcome = TrialOutcome.RUNNING,
    val checkpoints: Int = 0,
    val rowCount: Long = 0,
    /** Steps and walked distance from step + heading tracking (FR-DIR-01). */
    val steps: Int = 0,
    val walkedM: Float = 0f,
) {
    val durationMs: Long? get() = endElapsedNs?.let { (it - startElapsedNs) / 1_000_000 }
}
