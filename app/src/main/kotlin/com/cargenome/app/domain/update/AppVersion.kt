package com.cargenome.app.domain.update

enum class UpdateType {
    Major,
    Minor,
    Patch,
}

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String? = null,
    val raw: String,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)
        return 0
    }

    fun isNewerThan(other: AppVersion): Boolean = this > other

    fun determineUpdateType(from: AppVersion): UpdateType? {
        if (!isNewerThan(from)) return null
        return when {
            this.major > from.major -> UpdateType.Major
            this.minor > from.minor -> UpdateType.Minor
            else -> UpdateType.Patch
        }
    }

    companion object {
        private val VERSION_REGEX = Regex(
            """^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-._](.+))?$""",
        )

        fun parse(versionStr: String): AppVersion {
            val clean = versionStr.trim()
            val match = VERSION_REGEX.find(clean)
            return if (match != null) {
                val major = match.groupValues[1].toIntOrNull() ?: 0
                val minor = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                val patch = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                val suffix = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
                AppVersion(major, minor, patch, suffix, clean)
            } else {
                AppVersion(0, 0, 0, null, clean)
            }
        }
    }
}
