package org.nunocky.speechrecognitionapistudy.ui.file

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