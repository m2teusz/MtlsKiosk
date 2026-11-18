package pl.homelab.mtlskiosk

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kiosk", Context.MODE_PRIVATE)

    /** Adres startowy (np. https://ha.example.com). */
    var startUrl: String
        get() = sp.getString("start_url", BuildConfig.DEFAULT_URL) ?: BuildConfig.DEFAULT_URL
        set(v) = sp.edit().putString("start_url", v).apply()

    /** Alias certyfikatu klienta z magazynu systemowego (KeyChain). */
    var certAlias: String?
        get() = sp.getString("cert_alias", null)
        set(v) = sp.edit().putString("cert_alias", v).apply()

    /** Czy przy zamknięciu kasować też cookies + localStorage (= wylogowanie). */
    var clearStorage: Boolean
        get() = sp.getBoolean("clear_storage", false)
        set(v) = sp.edit().putBoolean("clear_storage", v).apply()

    /** Ukrywanie paska statusu i nawigacji (tryb immersive). */
    var fullscreen: Boolean
        get() = sp.getBoolean("fullscreen", false)
        set(v) = sp.edit().putBoolean("fullscreen", v).apply()
}
