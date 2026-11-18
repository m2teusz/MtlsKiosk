package pl.homelab.mtlskiosk

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var urlField: EditText
    private lateinit var certLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        urlField = findViewById(R.id.url)
        certLabel = findViewById(R.id.certAlias)
        val clearSwitch = findViewById<SwitchCompat>(R.id.clearStorage)

        urlField.setText(prefs.startUrl)
        clearSwitch.isChecked = prefs.clearStorage
        val fullSwitch = findViewById<SwitchCompat>(R.id.fullscreen)
        fullSwitch.isChecked = prefs.fullscreen
        refreshCert()

        findViewById<Button>(R.id.chooseCert).setOnClickListener { chooseCert() }
        findViewById<Button>(R.id.forgetCert).setOnClickListener {
            prefs.certAlias = null
            refreshCert()
        }
        findViewById<Button>(R.id.save).setOnClickListener {
            val url = normalize(urlField.text.toString())
            if (url == null) {
                urlField.error = "Podaj adres, np. https://ha.example.com"
                return@setOnClickListener
            }
            prefs.startUrl = url
            prefs.clearStorage = clearSwitch.isChecked
            prefs.fullscreen = fullSwitch.isChecked
            setResult(Activity.RESULT_OK)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (prefs.startUrl.isBlank())
                    Toast.makeText(this@SettingsActivity, "Ustaw adres startowy", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }

    private fun normalize(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val withScheme = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
        return if (Uri.parse(withScheme).host.isNullOrBlank()) null else withScheme
    }

    private fun chooseCert() {
        val uri = normalize(urlField.text.toString())?.let { Uri.parse(it) }
        KeyChain.choosePrivateKeyAlias(
            this,
            KeyChainAliasCallback { alias ->
                if (alias != null) prefs.certAlias = alias
                runOnUiThread { refreshCert() }
            },
            null, null, uri?.host, if (uri != null && uri.port > 0) uri.port else 443, prefs.certAlias
        )
    }

    private fun refreshCert() {
        certLabel.text = prefs.certAlias ?: "(nie wybrano – zapyta przy pierwszym połączeniu)"
    }
}
