package si.safeer.tv

import org.json.JSONObject

object SafeerDbg {
    // #region agent log
    fun log(hypothesisId: String, location: String, message: String, data: JSONObject = JSONObject()) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.d("SafeerDbg", "$hypothesisId $location $message $data")
    }
    // #endregion
}
