package com.tuusuario.teclado

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.Button
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MiTecladoService : InputMethodService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.teclado_view, null)

        val btnTraducir = view.findViewById<Button>(R.id.btnTraducir)
        btnTraducir.setOnClickListener {
            dispararTraduccion()
        }

        return view
    }

    private fun dispararTraduccion() {
        val inputConnection = currentInputConnection ?: return
        val textoEspanol = inputConnection.getTextBeforeCursor(500, 0).toString()
        if (textoEspanol.isBlank()) return

        serviceScope.launch {
            try {
                val url = "https://traductor-api-dyat.onrender.com/"
                
                val jsonBody = JSONObject().apply {
                    put("texto", textoEspanol)
                    put("acento", "US")
                    put("tono", "Informal")
                }

                val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val jsonRespuesta = JSONObject(responseData ?: "")
                        val textoIngles = jsonRespuesta.getString("traduccion")

                        withContext(Dispatchers.Main) {
                            inputConnection.deleteSurroundingText(textoEspanol.length, 0)
                            inputConnection.commitText(textoIngles, 1)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
