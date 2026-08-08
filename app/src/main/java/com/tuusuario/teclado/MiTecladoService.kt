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

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var symbolsAltKeyboard: Keyboard

    private var isCaps = false
    private var acentoSeleccionado = "US"

    private lateinit var btnUS: Button
    private lateinit var btnUK: Button
    private lateinit var btnTraducir: Button

    companion object {
        const val CODE_SHIFT = -1
        const val CODE_DELETE = -5
        const val CODE_ENTER = 10
        const val CODE_SPACE = 32
        const val CODE_MODE_SYMBOLS = -2
        const val CODE_MODE_ALT = -3
        const val CODE_MODE_ABC = -6
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.teclado_view, null)

        qwertyKeyboard = Keyboard(this, R.xml.keyboard_qwerty)
        symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)
        symbolsAltKeyboard = Keyboard(this, R.xml.keyboard_symbols_alt)

        keyboardView = view.findViewById(R.id.keyboardView)
        keyboardView.keyboard = qwertyKeyboard
        keyboardView.setOnKeyboardActionListener(this)

        btnUS = view.findViewById(R.id.btnAcentoUS)
        btnUK = view.findViewById(R.id.btnAcentoUK)
        btnTraducir = view.findViewById(R.id.btnTraducir)

        actualizarEstiloBotones()

        btnUS.setOnClickListener {
            acentoSeleccionado = "US"
            actualizarEstiloBotones()
        }

        btnUK.setOnClickListener {
            acentoSeleccionado = "UK"
            actualizarEstiloBotones()
        }

        btnTraducir.setOnClickListener {
            dispararTraduccion()
        }

        return view
    }

    private fun actualizarEstiloBotones() {
        if (acentoSeleccionado == "US") {
            btnUS.setBackgroundColor(Color.parseColor("#5B433B"))
            btnUS.setTextColor(Color.parseColor("#FFFFFF"))
            btnUK.setBackgroundColor(Color.parseColor("#3D322F"))
            btnUK.setTextColor(Color.parseColor("#C8B8B0"))
        } else {
            btnUK.setBackgroundColor(Color.parseColor("#5B433B"))
            btnUK.setTextColor(Color.parseColor("#FFFFFF"))
            btnUS.setBackgroundColor(Color.parseColor("#3D322F"))
            btnUS.setTextColor(Color.parseColor("#C8B8B0"))
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return

        when (primaryCode) {
            CODE_DELETE -> inputConnection.deleteSurroundingText(1, 0)

            CODE_SHIFT -> {
                isCaps = !isCaps
                qwertyKeyboard.isShifted = isCaps
                keyboardView.invalidateAllKeys()
            }

            CODE_ENTER -> inputConnection.commitText("\n", 1)

            CODE_SPACE -> inputConnection.commitText(" ", 1)

            CODE_MODE_SYMBOLS -> {
                keyboardView.keyboard = symbolsKeyboard
            }

            CODE_MODE_ALT -> {
                keyboardView.keyboard = symbolsAltKeyboard
            }

            CODE_MODE_ABC -> {
                keyboardView.keyboard = qwertyKeyboard
            }

            else -> {
                var codeChar = primaryCode.toChar()
                if (isCaps && codeChar.isLowerCase()) {
                    codeChar = codeChar.uppercaseChar()
                }
                inputConnection.commitText(codeChar.toString(), 1)
            }
        }
    }

    private fun dispararTraduccion() {
        val inputConnection = currentInputConnection ?: return
        val textoEspanol = inputConnection.getTextBeforeCursor(500, 0).toString()
        if (textoEspanol.isBlank()) return

        btnTraducir.text = "⏳ Traduciendo..."
        btnTraducir.isEnabled = false

        serviceScope.launch {
            try {
                val url = "https://api-dyat.onrender.com/api/traducir"

                val jsonBody = JSONObject().apply {
                    put("texto", textoEspanol)
                    put("acento", acentoSeleccionado)
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
            } finally {
                withContext(Dispatchers.Main) {
                    btnTraducir.text = "✨ Traducir"
                    btnTraducir.isEnabled = true
                }
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
