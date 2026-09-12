package si.safeer.tv
// Test stub: returns the resource name so tests can assert which text is shown.
object UiText {
    val language = "sl"
    fun get(resId: Int): String = R.string::class.java.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == resId }?.name ?: ""
    fun get(resId: Int, vararg args: Any?): String = get(resId) + args.joinToString(",", "(", ")")
}
