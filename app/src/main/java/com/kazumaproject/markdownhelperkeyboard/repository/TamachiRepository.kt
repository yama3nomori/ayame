package com.kazumaproject.markdownhelperkeyboard.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TamachiRepository @Inject constructor() {

    private var tamachiMap: Map<String, String>? = null

    suspend fun load(context: Context) {
        if (tamachiMap != null) return

        withContext(Dispatchers.IO) {
            try {
                val map = mutableMapOf<String, String>()
                // Tamachi.csv format: "kanji,detailed_reading"
                context.assets.open("Tamachi.csv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.forEachLine { line ->
                            val parts = line.split(",", limit = 2)
                            if (parts.size == 2) {
                                val kanji = parts[0].trim()
                                val reading = parts[1].trim()
                                if (kanji.isNotEmpty()) {
                                    map[kanji] = reading
                                }
                            }
                        }
                    }
                }
                tamachiMap = map
                Timber.d("Tamachi.csv loaded: ${map.size} entries")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Tamachi.csv")
            }
        }
    }

    /**
     * Given a string of text, returns the detailed reading if available in Tamachi.csv.
     * If not found, returns the character type detailed description.
     */
    fun getDetailedReading(text: String): String? {
        val map = tamachiMap ?: return null
        val sb = StringBuilder()
        var foundAny = false
        var lastTypeLabel: String? = null

        for (char in text) {
            val charStr = char.toString()
            val reading = map[charStr]
            if (reading != null) {
                if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                sb.append(reading)
                foundAny = true
                lastTypeLabel = null
            } else {
                val typeLabel = com.kazumaproject.markdownhelperkeyboard.utils.JapaneseCharacterUtils.getCharacterTypeDetailed(char)
                if (typeLabel != null) {
                    if (typeLabel == lastTypeLabel) {
                        sb.append(charStr)
                    } else {
                        if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                        sb.append("$typeLabel $charStr")
                        lastTypeLabel = typeLabel
                    }
                    foundAny = true
                } else {
                    if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                    sb.append(charStr)
                    lastTypeLabel = null
                }
            }
        }
        return if (foundAny) sb.toString() else null
    }
}
