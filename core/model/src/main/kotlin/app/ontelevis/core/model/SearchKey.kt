package app.ontelevis.core.model

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")

/**
 * Text as searched: lower case, accents removed, runs of spaces collapsed ("Québec  Été" ->
 * "quebec ete"). Stored with each movie / series and applied to what the user types, so a search
 * finds "Québec" from "quebec".
 */
fun searchKeyOf(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .replace(SPACES, " ")
        .trim()
