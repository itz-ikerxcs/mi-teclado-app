package com.tuusuario.teclado

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.widget.Button
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MiTecladoService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    
    private var acentoSeleccionado = "US" // Variable para guardar el acento actual
    private lateinit var btnUS: Button
    private lateinit var btnUK: Button

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.teclado_view, null)

        // Configurar panel de letras QWERTY
        val keyboardView = view.findViewById<KeyboardView>(R.id.keyboardView)
        val keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        // Referencias a los botones de acento
        btnUS = view.findViewById(R.id.btnAcentoUS)
        btnUK = view.findViewById(R.id.btnAcentoUK)

        // Estado inicial (US seleccionado por defecto)
        actualizarEstiloBotones()

        btnUS.setOnClickListener {
            acentoSeleccionado = "US"
            actualizarEstiloBotones()
        }

        btnUK.setOnClickListener {
            acentoSeleccionado = "UK"
            actualizarEstiloBotones()
        }

        // Botón de traducción
        val btnTraducir = view.findViewById<Button>(R.id.btnTraducir)
        btnTraducir.setOnClickListener {
            dispararTraduccion()
        }

        return view
    }

    private fun actualizarEstiloBotones() {
        if (acentoSeleccionado == "US") {
            btnUS.setBackgroundColor(Color.parseColor("#444444")) // Más oscuro si está activo
            btnUK.setBackgroundColor(Color.parseColor("#222222")) // Más claro si está inactivo
        } else {
            btnUK.setBackgroundColor(Color.parseColor("#444444"))
            btnUS.setBackgroundColor(Color.parseColor("#222222"))
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return
        when (primaryCode) {
            -5 -> inputConnection.deleteSurroundingText(1, 0) // Borrar
            10 -> inputConnection.commitText("\n", 1)         // Enter
            else -> {
                val codeChar = primaryCode.toChar()
                inputConnection.commitText(codeChar.toString(), 1)
            }
        }
    }

    private fun dispararTraduccion() {
        val inputConnection = currentInputConnection ?: return
        val textoEspanol = inputConnection.getTextBeforeCursor(500, 0).toString()
        if (textoEspanol.isBlank()) return

        serviceScope.launch {
            try {
                val url = "https://api-dyat.onrender.com/api/traducir"
                
                val jsonBody = JSONObject().apply {
                    put("texto", textoEspanol)
                    put("acento", acentoSeleccionado) // Envía "US" o "UK" según lo que elegiste
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

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
