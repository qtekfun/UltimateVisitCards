package com.qtekfun.ultimatevisitcards.domain.util

import java.util.UUID

/** Entity ids are UUID v4 stored as TEXT (see CONTRIBUTING.md). */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Uuid: IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    }
}
