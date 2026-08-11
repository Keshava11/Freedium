package com.ravi.freedium.store

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Outcome of one retention sweep. */
enum class CleanupStatus { SUCCESS, FAILED }

/**
 * An audit row for every run of the weekly retention sweep.
 *
 * The sweep deletes things while nobody is watching, at 2am, so a record of what it did -
 * and whether it worked at all - is the only way to know it is behaving. A failed run is
 * logged just as deliberately as a successful one.
 */
@Entity(tableName = "cleanup_log")
data class CleanupLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** When the sweep ran. */
    val runAt: Long = System.currentTimeMillis(),

    val status: CleanupStatus,

    /** How many notifications were removed. Zero is a perfectly good success. */
    val deletedCount: Int = 0,

    /** The cutoff used, so a surprising deletion count can be explained after the fact. */
    val cutoffTimestamp: Long = 0,

    /** Failure reason, or a short note on success. */
    val message: String? = null,

    /** How long the sweep took, in milliseconds. */
    val durationMs: Long = 0
)
