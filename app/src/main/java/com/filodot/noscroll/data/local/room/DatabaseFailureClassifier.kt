package com.filodot.noscroll.data.local.room

import android.database.sqlite.SQLiteDatabaseCorruptException

internal object DatabaseFailureClassifier {
    private val corruptionMarkers = listOf(
        "database disk image is malformed",
        "file is not a database",
        "database corruption",
        "database corrupt",
        "malformed database schema",
    )

    fun isCorruption(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .any { cause ->
                cause is SQLiteDatabaseCorruptException ||
                    corruptionMarkers.any { marker ->
                        cause.message?.contains(marker, ignoreCase = true) == true
                    }
            }
}
