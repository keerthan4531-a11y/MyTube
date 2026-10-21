package com.github.libretube.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import com.github.libretube.R
import com.github.libretube.databinding.ActivityAboutBinding
import com.github.libretube.helpers.ClipboardHelper
import com.github.libretube.helpers.IntentHelper
import com.github.libretube.ui.base.BaseActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class AboutActivity : BaseActivity() {
    private lateinit var binding: ActivityAboutBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.creatorGlassView.enableDynamicBackground = true
        binding.instagramGlassView.enableDynamicBackground = true

        // Cyber-tech entrance animations
        try {
            val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
                duration = 600
            }
            binding.heroSection.startAnimation(fadeIn)
            binding.creatorCard.startAnimation(fadeIn)
            binding.instagramCard.startAnimation(fadeIn)
            binding.featuresCard.startAnimation(fadeIn)
            binding.gifCard.startAnimation(fadeIn)
        } catch (_: Exception) { }

        // Setup texts
        binding.appTitle.text = "MyTube"
        binding.creatorName.text = "mokka coding"
        binding.creatorRole.text = "Lead Developer & Architect"

        val vName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "32.1"
        } catch (_: Exception) {
            "32.1"
        }
        binding.versionText.text = "Version $vName • Cyber Edition"

        // Instagram Click Listener
        binding.instagramCard.setOnClickListener {
            openInstagramProfile()
        }

        // Cards setup
        setupCard(binding.website, WEBSITE_URL)
        setupCard(binding.github, GITHUB_URL)

        binding.license.setOnClickListener {
            showLicense()
        }
        binding.license.setOnLongClickListener {
            onLongClick(LICENSE_URL)
            true
        }

        binding.device.setOnClickListener {
            showDeviceInfo()
        }

        binding.featuresCard.setOnClickListener {
            showFeaturesDialog()
        }

        // Start Animated GIF playback
        playGifAnimation()
    }

    private fun openInstagramProfile() {
        val instaUri = Uri.parse(INSTAGRAM_URL)
        val intent = Intent(Intent.ACTION_VIEW, instaUri)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            ClipboardHelper.save(this, text = INSTAGRAM_URL)
            Snackbar.make(binding.root, "Instagram link copied!", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun playGifAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(resources, R.drawable.lmeb)
                val drawable = ImageDecoder.decodeDrawable(source)
                binding.gifView.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable.start()
                }
            } catch (_: Exception) {
                binding.gifView.setImageResource(R.drawable.lmeb)
            }
        } else {
            binding.gifView.setImageResource(R.drawable.lmeb)
        }
    }

    private fun showFeaturesDialog() {
        val msg = """
            ◆ YouTube Feed Design
            Full-width 16:9 cards with duration pills & three-dot actions
            
            ◆ Smart Profile & Library Hub
            Personalized center for playlists, watch history & downloads
            
            ◆ Background & PiP Playback
            Listen to videos with your screen off or while multitasking
            
            ◆ Built-in SponsorBlock & Ad Free
            Skip sponsors, intros, and ads automatically
            
            ◆ Offline Media Downloader
            Download videos and audio directly to your device
            
            ◆ Privacy Architecture
            Zero Google tracking, custom instances and pure open freedom
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("MyTube Cyber Features")
            .setMessage(msg)
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    private fun setupCard(card: MaterialCardView, link: String) {
        card.setOnClickListener {
            IntentHelper.openLinkFromHref(this, supportFragmentManager, link)
        }
        card.setOnLongClickListener {
            onLongClick(link)
            true
        }
    }

    private fun onLongClick(href: String) {
        ClipboardHelper.save(this, text = href)
        Snackbar.make(
            binding.root,
            R.string.copied_to_clipboard,
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.open_copied) {
                IntentHelper.openLinkFromHref(this, supportFragmentManager, href)
            }
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .show()
    }

    private fun showLicense() {
        val licenseHtml = try {
            assets.open("gpl3.html")
                .bufferedReader()
                .use { it.readText() }
                .parseAsHtml(HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
        } catch (_: Exception) {
            "GPL-3.0 License".parseAsHtml()
        }

        MaterialAlertDialogBuilder(this)
            .setPositiveButton(getString(R.string.okay), null)
            .setMessage(licenseHtml)
            .show()
    }

    private fun showDeviceInfo() {
        val metrics = Resources.getSystem().displayMetrics

        val text = "Manufacturer: ${Build.MANUFACTURER}\n" +
                "Model: ${Build.MODEL}\n" +
                "Board: ${Build.BOARD}\n" +
                "Arch: ${Build.SUPPORTED_ABIS[0]}\n" +
                "Android SDK: ${Build.VERSION.SDK_INT}\n" +
                "OS: Android ${Build.VERSION.RELEASE}\n" +
                "Display: ${metrics.widthPixels}x${metrics.heightPixels}\n" +
                "Font scale: ${Resources.getSystem().configuration.fontScale}"

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_info)
            .setMessage(text)
            .setNegativeButton(R.string.copy_tooltip) { _, _ ->
                ClipboardHelper.save(this@AboutActivity, text = text)
            }
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    companion object {
        const val DONATE_URL = "https://github.com"
        const val INSTAGRAM_URL = "https://www.instagram.com/dark.shadow_4531?stkn=MTZua29kbXpoOGhnMA=="
        private const val WEBSITE_URL = "https://github.com"
        const val GITHUB_URL = "https://github.com"
        private const val LICENSE_URL = "https://gnu.org/"
    }
}
