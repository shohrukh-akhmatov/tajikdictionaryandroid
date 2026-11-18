package tj.tajikdictionary.core

/**
 * Public links used on share cards and in share text (same values as iOS `AppLinks`).
 * The download page lives on GitHub Pages — change [SHARE_URL] here once it moves (single source of truth).
 */
object AppLinks {
    const val SHARE_URL = "https://shohrukh-akhmatov.github.io/tajikdictionary/"
    const val HASHTAG = "#LearnTajik"

    /** Host + path without scheme, for printing on the card ("shohrukh-akhmatov.github.io/tajikdictionary"). */
    val displayUrl: String
        get() = SHARE_URL.removePrefix("https://").removePrefix("http://").removeSuffix("/")
}
