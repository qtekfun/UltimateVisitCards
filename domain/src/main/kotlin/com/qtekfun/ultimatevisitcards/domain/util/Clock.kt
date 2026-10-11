package com.qtekfun.ultimatevisitcards.domain.util

/** Source of the current time (epoch millis UTC), injectable so tests are deterministic. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}
