package com.flirttime.feature.profile.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityHelpSupportBinding

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpSupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupListeners()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(top = statusBarInsets.top, bottom = navBarInsets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.cardFaqs.setOnClickListener { openFaq(null) }
        binding.cardEmailUs.setOnClickListener { emailSupport() }
        binding.rowAccountTopic.setOnClickListener { openFaq(FAQCategory.ACCOUNT) }
        binding.rowMatchesTopic.setOnClickListener { openFaq(FAQCategory.MATCHES) }
        binding.rowPremiumTopic.setOnClickListener { openFaq(FAQCategory.PREMIUM) }
        binding.rowSafetyTopic.setOnClickListener { openFaq(FAQCategory.SAFETY) }
    }

    private fun openFaq(category: FAQCategory?) {
        AppNavigator.navigateTo(this, Destination.Faq(category?.name))
    }

    private fun emailSupport() {
        val email = SUPPORT_EMAIL
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, "Flirt Time support")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            copySupportEmail()
        }
    }

    private fun copySupportEmail() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Support email", SUPPORT_EMAIL))
        Toast.makeText(this, "Support email copied: $SUPPORT_EMAIL", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val SUPPORT_EMAIL = "support@flirttime.in"
    }
}
