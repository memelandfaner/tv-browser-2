package android.webkit
class WebResourceResponse(val mimeType: String, val encoding: String, val statusCode: Int, val reasonPhrase: String, val responseHeaders: Map<String, String>, val data: java.io.InputStream)
