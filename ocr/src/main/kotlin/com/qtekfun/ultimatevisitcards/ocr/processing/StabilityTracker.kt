package com.qtekfun.ultimatevisitcards.ocr.processing

/**
 * Decides when to shoot automatically: the detected card has stayed still (every corner moved less than
 * [tolerance] of the image diagonal) for [holdMillis] and the frame is sharp. Pure state machine fed with
 * one observation per analysed frame.
 */
class StabilityTracker(private val holdMillis: Long = 700, private val tolerance: Double = 0.02) {
    private var reference: Quad? = null
    private var since: Long = 0
    private var fired = false

    /** Returns true once per stable period; afterwards it needs the card to move or disappear to fire again. */
    fun onFrame(quad: Quad?, sharp: Boolean, diagonal: Double, timeMillis: Long): Boolean {
        val previous = reference
        if (quad == null || !sharp) {
            reference = null
            return false
        }
        val moved = previous == null ||
            previous.points.zip(quad.points).any { (a, b) -> a.distanceTo(b) > tolerance * diagonal }
        if (moved) {
            reference = quad
            since = timeMillis
            fired = false
            return false
        }
        if (!fired && timeMillis - since >= holdMillis) {
            fired = true
            return true
        }
        return false
    }

    fun reset() {
        reference = null
        fired = false
    }
}
