package com.aiza.core.direction

import com.aiza.core.model.Sample
import com.aiza.core.model.WedgeDecision

/**
 * The direction-finding core (FR-DIR-01..08). Implementations combine PDR (steps + heading),
 * per-stretch hotter/colder labels and a particle filter over tag positions.
 *
 * The same implementation runs live on the phone and offline in tools:replay (FR-TRL-06).
 */
interface DirectionEngine {
    /** Feeds samples in timestamp order; returns the current wedge decision. */
    fun update(sample: Sample): WedgeDecision?
    fun reset()
}

/** Placeholder until the particle filter exists: never shows a wedge (NFR-ACC-02 — prefer no wedge). */
class NoWedgeEngine : DirectionEngine {
    override fun update(sample: Sample): WedgeDecision? = null
    override fun reset() = Unit
}
