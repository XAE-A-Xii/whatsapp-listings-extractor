package com.privacy.whatsappdecryptor.core.inventory

import java.time.LocalDate

/**
 * Timeframe stored in UI state. [LAST_WEEK] is the default option (value 1).
 * [THREE_MONTHS] keeps the calendar-month cutoff.
 */
object InventoryWindow {
    const val LAST_WEEK = 1L
    const val THREE_MONTHS = 3L

    fun cutoff(referenceDate: LocalDate, window: Long): LocalDate {
        return if (window == LAST_WEEK) {
            referenceDate.minusDays(7)
        } else {
            referenceDate.minusMonths(window).withDayOfMonth(1)
        }
    }

    fun suffix(window: Long): String = if (window == LAST_WEEK) "7d" else "${window}m"

    fun databaseName(window: Long): String = "listings_${suffix(window)}.db"
}
