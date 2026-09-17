package com.cargenome.app.domain.vin

import com.cargenome.vin.OfflineVinDecoder
import com.cargenome.vin.VinDecodeResult
import com.cargenome.vin.VinFormat

object VinOcrParser {
    private val VIN_REGEX = Regex("[A-HJ-NPR-Z0-9]{17}")
    private val defaultDecoder = OfflineVinDecoder()

    /**
     * Parses raw text from OCR and returns all valid 17-character VINs found.
     * Results are ordered with valid check-digit VINs first, then other validly decoded VINs.
     */
    fun parseVins(rawText: String, decoder: OfflineVinDecoder = defaultDecoder): List<String> {
        val candidates = mutableSetOf<String>()

        for (rawLine in rawText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Split line into tokens by whitespace, asterisks, colons, punctuation
            val rawTokens = line.split(Regex("""[\s*:,;|]+"""))
                .map { cleanToken(it) }
                .filter { it.isNotEmpty() }

            // 1. Check individual tokens (with hyphens/dots cleaned)
            for (token in rawTokens) {
                val cleaned = token
                    .removePrefix("VIN")
                    .removePrefix("ВИН")

                if (cleaned.length == VinFormat.LENGTH) {
                    addIfValidCandidate(cleaned, candidates)
                } else if (cleaned.length > VinFormat.LENGTH) {
                    for (i in 0..cleaned.length - VinFormat.LENGTH) {
                        val slice = cleaned.substring(i, i + VinFormat.LENGTH)
                        addIfValidCandidate(slice, candidates)
                    }
                }
            }

            // 2. Check if a sequence of 2..5 short tokens (min length 2 each) concatenates to 17 chars
            // e.g. ["WVW", "ZZZ", "1JZ", "3W", "386752"]
            val shortTokens = rawTokens.filter {
                it != "VIN" && it != "ВИН" && it.length >= 2 && it.all { ch -> ch.isLetterOrDigit() }
            }
            for (start in shortTokens.indices) {
                var combined = ""
                val maxEnd = minOf(shortTokens.size, start + 5)
                for (end in start until maxEnd) {
                    combined += shortTokens[end]
                    if (combined.length == VinFormat.LENGTH) {
                        addIfValidCandidate(combined, candidates)
                        break
                    } else if (combined.length > VinFormat.LENGTH) {
                        break
                    }
                }
            }
        }

        // Validate and rank candidates
        return candidates
            .filter { looksLikeVin(it) }
            .mapNotNull { cand ->
                when (val result = decoder.decode(cand)) {
                    is VinDecodeResult.Decoded -> {
                        val hasCountry = result.value.country != null
                        val hasCheckMatch = result.value.checkDigit.matches
                        if (hasCountry || hasCheckMatch) {
                            cand to hasCheckMatch
                        } else {
                            null
                        }
                    }
                    is VinDecodeResult.Malformed -> null
                }
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * ISO 3779 requires VINs to contain both letters and digits, and the serial
     * number portion (ending positions 15-17) is always numeric digits.
     */
    private fun looksLikeVin(cand: String): Boolean {
        if (cand.length != VinFormat.LENGTH) return false
        val hasDigits = cand.any { it.isDigit() }
        val hasLetters = cand.any { it.isLetter() }
        val endsWithDigits = cand.takeLast(3).all { it.isDigit() }
        return hasDigits && hasLetters && endsWithDigits
    }

    private fun addIfValidCandidate(raw: String, out: MutableSet<String>) {
        val fixed = fixOcrConfusions(raw)
        if (VIN_REGEX.matches(fixed)) {
            out.add(fixed)
        }
    }

    private fun cleanToken(token: String): String {
        return token.uppercase()
            .replace("-", "")
            .replace("_", "")
            .replace(".", "")
            .replace("/", "")
            .replace("\\", "")
            .replace("|", "1")
    }

    private fun fixOcrConfusions(s: String): String {
        return s.uppercase()
            .replace('O', '0')
            .replace('I', '1')
            .replace('Q', '0')
    }
}
