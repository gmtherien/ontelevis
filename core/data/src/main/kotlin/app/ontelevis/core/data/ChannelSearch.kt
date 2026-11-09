package app.ontelevis.core.data

import app.ontelevis.core.data.db.channelSearchKey

/**
 * Best channel for a spoken or typed name ("RDS", "TSN 2"): the cleaned titles that equal the query
 * first, then those starting with it, then those containing it; between equals, favourites, then
 * recently watched, then the shortest title. [keyOf] gives a candidate's search key.
 */
internal fun <T> bestChannelMatches(
    query: String,
    candidates: List<T>,
    keyOf: (T) -> String,
    idOf: (T) -> String,
    favorites: Set<String>,
    recent: List<String>,
): List<T> {
    val key = channelSearchKey(query)
    if (key.isEmpty()) return emptyList()
    val recentRank = recent.withIndex().associate { (index, id) -> id to index }
    return candidates
        .mapNotNull { candidate ->
            val title = keyOf(candidate)
            val rank =
                when {
                    title == key -> 0
                    title.startsWith("$key ") -> 1
                    title.startsWith(key) -> 2
                    title.contains(key) -> 3
                    else -> return@mapNotNull null
                }
            candidate to rank
        }.sortedWith(
            compareBy<Pair<T, Int>>(
                { it.second },
                { idOf(it.first) !in favorites },
                { recentRank[idOf(it.first)] ?: Int.MAX_VALUE },
                { keyOf(it.first).length },
            ),
        ).map { it.first }
}
