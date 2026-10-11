package com.qtekfun.ultimatevisitcards.ui.common

import java.text.DateFormat
import java.util.Date

/** Dates are stored as epoch millis UTC and only formatted here, in the UI, in the device locale and zone. */
fun formatDateTime(epochMillis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

fun formatDate(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
