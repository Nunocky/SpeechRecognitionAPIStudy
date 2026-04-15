package org.nunocky.flutter_genai_mlkit_speech_recognition

class ResultBuffer {
    private val results = mutableListOf<String>()

    fun add(text: String) {
        val normalized = text.trim()
        if (normalized.isNotEmpty()) {
            results += normalized
        }
    }

    fun clear() {
        results.clear()
    }

    fun getAggregated(): String = results.joinToString(separator = " ")
}
