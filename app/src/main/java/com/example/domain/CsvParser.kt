package com.example.domain

object CsvParser {
    /**
     * Parses a single CSV line securely, taking quotes and commas into account.
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = java.lang.StringBuilder()
        
        if (line.contains("\t")) {
            return line.split("\t").map { it.trim().removeSurrounding("\"") }
        }
        
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                // RFC-4180 Lookahead: If the next character is also a double quote, treat it as an escaped literal quote
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++ // Skip the second quote of the escaped pair
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result.map { it.removeSurrounding("\"").trim() }
    }
}
