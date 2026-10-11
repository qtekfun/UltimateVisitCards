package com.qtekfun.ultimatevisitcards.domain.model

/** Errors a domain operation can report. */
sealed interface DomainError {
    /** The entity with this id does not exist (any more). */
    data class NotFound(val id: String) : DomainError

    /** A required field is missing or malformed. */
    data class Invalid(val field: Field) : DomainError

    /** Another entity already uses this unique value (e.g. a sector name). */
    data class Conflict(val field: Field, val existingId: String) : DomainError

    /** The operation would break a rule of the model. */
    data class NotAllowed(val rule: Rule) : DomainError

    /** The storage layer failed unexpectedly. */
    data class Storage(val cause: Throwable) : DomainError

    enum class Field { NAME }

    enum class Rule {
        /** A contact's only card cannot be deleted without deleting the contact (docs/06-duplicados-historico.md). */
        ONLY_CARD_OF_CONTACT
    }
}
