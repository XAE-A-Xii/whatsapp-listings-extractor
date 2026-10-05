package com.privacy.whatsappdecryptor.core.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InventoryWindowTest {

    @Test
    fun `last week starts seven days before the latest message date`() {
        val latest = LocalDate.of(2026, 10, 5)
        assertEquals(LocalDate.of(2026, 9, 28), InventoryWindow.cutoff(latest, InventoryWindow.LAST_WEEK))
        assertEquals("7d", InventoryWindow.suffix(InventoryWindow.LAST_WEEK))
        assertEquals("listings_7d.db", InventoryWindow.databaseName(InventoryWindow.LAST_WEEK))
    }

    @Test
    fun `three months still starts on the first of the month`() {
        val latest = LocalDate.of(2026, 10, 5)
        assertEquals(LocalDate.of(2026, 7, 1), InventoryWindow.cutoff(latest, InventoryWindow.THREE_MONTHS))
        assertEquals("3m", InventoryWindow.suffix(InventoryWindow.THREE_MONTHS))
    }
}
