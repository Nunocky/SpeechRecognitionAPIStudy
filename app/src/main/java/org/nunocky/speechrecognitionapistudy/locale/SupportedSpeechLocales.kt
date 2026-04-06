package org.nunocky.speechrecognitionapistudy.locale

data class LocaleOption(val tag: String, val displayName: String)

object SupportedSpeechLocales {

    const val DefaultLocaleTag = "ja-JP"

    val options: List<LocaleOption> = listOf(
        LocaleOption("en-US",       "English (en-US)"),
        LocaleOption("fr-FR",       "Français (fr-FR) β"),
        LocaleOption("it-IT",       "Italiano (it-IT) β"),
        LocaleOption("de-DE",       "Deutsch (de-DE) β"),
        LocaleOption("es-ES",       "Español (es-ES) β"),
        LocaleOption("hi-IN",       "हिन्दी (hi-IN) β"),
        LocaleOption("ja-JP",       "日本語 (ja-JP) β"),
        LocaleOption("pt-BR",       "Português (pt-BR) β"),
        LocaleOption("tr-TR",       "Türkçe (tr-TR) β"),
        LocaleOption("pl-PL",       "Polski (pl-PL) β"),
        LocaleOption("cmn-Hans-CN", "中文 简体 (cmn-Hans-CN) β"),
        LocaleOption("ko-KR",       "한국어 (ko-KR) β"),
        LocaleOption("cmn-Hant-TW", "中文 繁體 (cmn-Hant-TW) β"),
        LocaleOption("ru-RU",       "Русский (ru-RU) β"),
        LocaleOption("vi-VN",       "Tiếng Việt (vi-VN) β"),
    )

    private val validTags: Set<String> = options.map { it.tag }.toSet()

    fun sanitize(localeTag: String): String =
        if (localeTag in validTags) localeTag else DefaultLocaleTag
}
