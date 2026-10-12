package com.qtekfun.ultimatevisitcards.data.backup

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades the `database.json` of an older backup format to the current one, one version at a time
 * (docs/05 §3: «menor → migrador en cadena v1→v2…»). Version 1 is the current one: no steps yet. A new
 * format version adds its step here, together with a test that restores a backup of the previous version.
 */
object BackupMigrations {
    /** Step from version N to N + 1, by N. */
    private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun upgrade(version: Int, database: JsonObject): JsonObject {
        var current = database
        for (from in version until BackupFormat.VERSION) {
            val step = steps[from] ?: throw IllegalStateException("No migration from backup version $from")
            current = step(current)
        }
        return current
    }
}
