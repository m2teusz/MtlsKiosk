package pl.homelab.mtlskiosk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.view.View
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            fileCallback?.onReceiveValue(parseFiles(res.resultCode, res.data))
            fileCallback = null
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            enterImmersive()
            when {
                prefs.startUrl.isBlank() -> finishAndRemoveTask()
                res.resultCode == Activity.RESULT_OK -> wipe { webView.loadUrl(prefs.startUrl) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        // Sprzątanie także na starcie: zamknięcie przez "swipe" z ostatnich aplikacji
        // nie gwarantuje wywołania onDestroy().
        cacheDir.deleteRecursively()

        // WebView ignoruje padding, więc odstęp od pasków systemowych dostaje kontener
        // z jednolitym tłem — paski są "puste", a ikony na nich zawsze czytelne.
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        setupInsets()
        enterImmersive()
        configureWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else showMenu()
            }
        })

        wipe {
            if (prefs.startUrl.isBlank()) openSettings()
            else webView.loadUrl(prefs.startUrl)
        }
    }

    // ---------- Paski systemowe ----------

    /** Widoczne paski (domyślnie) albo tryb pełnoekranowy — wg ustawień. */
    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // tło pod paskami jest czarne -> jasne ikony
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            if (prefs.fullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** Strona nie wchodzi pod paski systemowe/wycięcie, klawiatura nie zasłania pól. */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v: View, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (prefs.fullscreen) {
                v.setPadding(0, 0, 0, ime.bottom)
            } else {
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            }
            insets
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.fullscreen) enterImmersive()
    }

    // ---------- WebView ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                // tel:, mailto:, intent: itp. -> systemowa aplikacja
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: ActivityNotFoundException) { }
                return true
            }

            override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
                val alias = prefs.certAlias
                if (alias != null) proceedWithAlias(alias, request) else pickAlias(request)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val html = """
                    <html><body style="background:#111;color:#eee;font-family:sans-serif;padding:24px">
                    <h3>Nie można załadować strony</h3>
                    <p>${error.description}</p>
                    <p style="color:#999">Wstecz → menu → Odśwież</p>
                    </body></html>
                """.trimIndent()
                view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }
    }

    // ---------- mTLS: certyfikat klienta z magazynu Androida ----------

    /** Systemowy wybór certyfikatu; wybór (i uprawnienie do klucza) jest zapamiętywany. */
    private fun pickAlias(request: ClientCertRequest) {
        KeyChain.choosePrivateKeyAlias(
            this,
            KeyChainAliasCallback { alias ->
                if (alias == null) {
                    main.post { request.cancel() }
                } else {
                    prefs.certAlias = alias
                    proceedWithAlias(alias, request, allowRepick = false)
                }
            },
            request.keyTypes, request.principals, request.host, request.port, prefs.certAlias
        )
    }

    private fun proceedWithAlias(alias: String, request: ClientCertRequest, allowRepick: Boolean = true) {
        io.execute {
            val ok = try {
                val key = KeyChain.getPrivateKey(this, alias)
                val chain = KeyChain.getCertificateChain(this, alias)
                if (key != null && chain != null) {
                    main.post { request.proceed(key, chain) }
                    true
                } else false
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                // certyfikat usunięty / brak uprawnień -> zapytaj ponownie
                prefs.certAlias = null
                main.post { if (allowRepick) pickAlias(request) else request.cancel() }
            }
        }
    }

    // ---------- Czyszczenie ----------

    private fun wipe(then: (() -> Unit)? = null) {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        WebView.clearClientCertPreferences(null) // WebView nie pamięta decyzji o certyfikacie między sesjami
        if (prefs.clearStorage) {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                then?.invoke()
            }
        } else {
            then?.invoke()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            wipe()
            cacheDir.deleteRecursively()
        }
        webView.destroy()
        io.shutdown()
        super.onDestroy()
    }

    // ---------- Menu (Wstecz na pierwszej stronie) ----------

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Odśwież", "Strona startowa", "Ustawienia", "Zamknij")) { _, which ->
                when (which) {
                    0 -> webView.reload()
                    1 -> webView.loadUrl(prefs.startUrl)
                    2 -> openSettings()
                    3 -> finishAndRemoveTask()
                }
            }
            .show()
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    // ---------- Upload plików (np. Immich) ----------

    private fun parseFiles(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            return Array(clip.itemCount) { clip.getItemAt(it).uri }
        }
        return data.data?.let { arrayOf(it) }
    }
}
