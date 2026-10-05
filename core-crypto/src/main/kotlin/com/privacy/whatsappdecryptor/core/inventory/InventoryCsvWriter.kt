package com.privacy.whatsappdecryptor.core.inventory

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object InventoryCsvWriter {

    val COLUMNS = listOf(
        "SOCIETY",
        "PROJECT LIST STATUS",
        "SEC ",
        "AREA ",
        "ACCO",
        "FLOOR",
        "FLAT NO",
        "DEALER NAME ",
        "PHONE NO",
        "PRICE",
        "FULL MESSAGE",
        "is_duplicate"
    )

    fun subExcelFileName(projectName: String, months: Long): String {
        val sanitized = projectName.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
        val name = if (sanitized.isEmpty()) "Project" else sanitized
        return "Inventory_${name}_${InventoryWindow.suffix(months)}.csv"
    }

    fun singleLineCsvText(value: String?): String {
        return (value ?: "")
            .replace(Regex("\\s*(?:\\r\\n|\\r|\\n)+\\s*"), " | ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    fun csvCell(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        var text = value

        // Prevent Excel formula injection
        if (Regex("^\\s*[=+\\-@]").containsMatchIn(text)) {
            text = "'$text"
        }

        if (text.contains('"') || text.contains(',') || text.contains('\r') || text.contains('\n')) {
            return "\"${text.replace("\"", "\"\"")}\""
        }
        return text
    }

    fun writeCsv(rows: List<ImportantDealerRow>, outputStream: OutputStream) {
        writeCsv(outputStream) { emit -> rows.forEach(emit) }
    }

    fun writeCsv(outputStream: OutputStream, produce: ((ImportantDealerRow) -> Unit) -> Unit) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))

        // Write UTF-8 BOM so Excel on Android/Windows displays characters correctly
        writer.write("\uFEFF")

        // Header line
        writer.write(COLUMNS.map { csvCell(it) }.joinToString(","))
        writer.write("\r\n")

        produce { row ->
            val cells = listOf(
                csvCell(row.society),
                csvCell(row.projectListStatus),
                csvCell(row.sec),
                csvCell(row.area),
                csvCell(row.acco),
                csvCell(row.floor),
                csvCell(row.flatNo),
                csvCell(row.dealerName),
                csvCell(row.phoneNo),
                csvCell(row.price),
                csvCell(singleLineCsvText(row.fullMessage)),
                csvCell(row.isDuplicate.toString())
            )
            writer.write(cells.joinToString(","))
            writer.write("\r\n")
        }

        writer.flush()
    }
}
