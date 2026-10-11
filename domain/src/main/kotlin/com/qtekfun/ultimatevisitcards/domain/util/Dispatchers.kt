package com.qtekfun.ultimatevisitcards.domain.util

import javax.inject.Qualifier

/** Dispatcher for blocking I/O (database, files). Injected, never hard-coded (see CLAUDE.md). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for CPU-bound work (image processing, field extraction). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
