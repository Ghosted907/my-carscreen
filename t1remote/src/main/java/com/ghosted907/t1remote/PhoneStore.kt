package com.ghosted907.t1remote

/** A car button fires only after a continuous hold; pure so it is unit-tested. */
object HoldPolicy {
    const val HOLD_MS = 1000L
    fun fires(pressedAt: Long, releasedAt: Long?, now: Long): Boolean =
        releasedAt == null && now - pressedAt >= HOLD_MS
}
