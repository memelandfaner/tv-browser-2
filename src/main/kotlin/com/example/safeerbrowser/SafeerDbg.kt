package com.example.safeerbrowser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SafeerDbg {
    // #region agent log
    fun log(hypothesisId: String, location: String, message: String, data: JSONObject = JSONObject()) {
        android.util.Log.d("SafeerDbg", "$hypothesisId $location $message $data")
        Thread {
            try {
                val payload = JSONObject()
                    .put("sessionId", "f2a2eb")
                    .put("hypothesisId", hypothesisId)
                    .put("location", location)
                    .put("message", message)
                    .put("data", data)
                    .put("timestamp", System.currentTimeMillis())
                    .put("runId", "post-fix")
                val conn = URL("http://127.0.0.1:7772/ingest/9efdbf26-7b34-4eef-91e7-a286d69bca1e")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Debug-Session-Id", "f2a2eb")
                conn.doOutput = true
                conn.connectTimeout = 600
                conn.readTimeout = 600
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                try { conn.inputStream.close() } catch (_: Exception) {}
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
    // #endregion
}
