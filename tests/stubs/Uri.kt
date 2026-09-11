package android.net
class Uri private constructor(private val uri: java.net.URI) {
    val host: String? get() = uri.host
    companion object {
        fun parse(value: String) = Uri(java.net.URI(value))
        fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }
}
