package android.content
open class Context {
    open val applicationContext: Context get() = this
    open val filesDir: java.io.File = java.nio.file.Files.createTempDirectory("ctx").toFile()
}
