package app.ontelevis.core.model

/**
 * An app version as written in release tags: `1.2.3` or `1.2.3-beta.4` (a leading `v` is allowed).
 * Ordered like semantic versions: `1.0.0-beta.2 < 1.0.0-beta.10 < 1.0.0 < 1.0.1`.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Pre-release parts ("beta", "4"); empty for a stable version. */
    val preRelease: List<String> = emptyList(),
) : Comparable<AppVersion> {
    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        // A stable version is newer than its pre-releases.
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return compareValues(other.preRelease.size.coerceAtMost(1), preRelease.size.coerceAtMost(1))
        }
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            val an = a.toIntOrNull()
            val bn = b.toIntOrNull()
            val c =
                when {
                    an != null && bn != null -> an.compareTo(bn)
                    an != null -> -1
                    bn != null -> 1
                    else -> a.compareTo(b)
                }
            if (c != 0) return c
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.]+))?$""")

        /** Null when [text] is not a version like the release tags. */
        fun parse(text: String?): AppVersion? {
            val m = PATTERN.matchEntire(text?.trim() ?: return null) ?: return null
            val (major, minor, patch, pre) = m.destructured
            return AppVersion(
                major.toInt(),
                minor.toInt(),
                patch.toInt(),
                pre.split('.').filter { it.isNotEmpty() },
            )
        }
    }
}
