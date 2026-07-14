package com.shanqijie.fitnessapp.domain

/**
 * Converts lightweight Markdown returned by AI providers into readable plain text for Compose Text.
 * Raw provider output remains unchanged in the local draft record.
 */
fun String.toReadableAiText(): String =
    lineSequence()
        .map { line ->
            line
                .replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
                .replace(Regex("^\\s*>\\s?"), "")
                .replace(Regex("^\\s*[-+*]\\s+"), "• ")
                .replace(Regex("^\\s*\\d+[.)]\\s+"), "• ")
        }
        .joinToString("\n")
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("__([^_]+)__"), "$1")
        .replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)"), "$1")
        .replace(Regex("(?<!_)_([^_\\n]+)_(?!_)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("(?m)^[=-]{3,}\\s*$"), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
