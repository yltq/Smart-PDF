package com.zunelo.pdfreader.guard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.zunelo.pdfreader.ads.config.AdConfigRepository
import com.zunelo.pdfreader.telemetry.CloakRepository
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub
import java.net.URLDecoder
import java.util.Locale

object AcquisitionGuard {
    @Volatile
    private var fakeAppDetected: Boolean? = null

    @Suppress("DEPRECATION")
    fun isAutomatedEnvironment(context: Context): Boolean {
        val testLab = runCatching {
            Settings.System.getString(context.contentResolver, "firebase.test.lab")
                .equals("true", ignoreCase = true)
        }.getOrDefault(false)
        return ActivityManager.isUserAMonkey() ||
            ActivityManager.isRunningInTestHarness() ||
            testLab
    }

    fun hasFakeLocationApp(context: Context): Boolean {
        fakeAppDetected?.let { return it }
        return synchronized(this) {
            fakeAppDetected ?: queryFakeLocationApp(context.applicationContext).also {
                fakeAppDetected = it
            }
        }
    }

    fun isCloakBlocked(): Boolean = CloakRepository.isBlacklisted()

    fun isReferrerBlocked(): Boolean {
        val referrer = SmartPdfSignalHub.installReferrer()
        val rules = AdConfigRepository.referrerRules()
        if (rules.open == 0) return false
        if (referrer.isBlank()) return true

        val candidates = buildSet {
            add(referrer)
            decodeReferrer(referrer)?.takeIf(String::isNotBlank)?.let(::add)
        }
        findMatch(candidates, rules.backList)?.let { matchedToken ->
            SmartPdfSignalHub.saveBackReferrerToken(matchedToken)
            return true
        }
        return findMatch(candidates, rules.allowList) == null
    }

    fun isTestAd(context: Context): Boolean = TestAdState.isTestAd(context)

    fun isTestDevice(): Boolean {
        val brand = Build.BRAND.normalized()
        val device = Build.DEVICE.normalized()
        if (brand.startsWith("generic") && device.startsWith("generic")) return true

        val fingerprint = Build.FINGERPRINT.normalized()
        if (fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")) return true

        val hardware = Build.HARDWARE.normalized()
        if (hardware.contains("goldfish") || hardware.contains("ranchu")) return true

        val model = Build.MODEL.normalized()
        val manufacturer = Build.MANUFACTURER.normalized()
        if (
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            manufacturer.contains("genymotion")
        ) {
            return true
        }

        val product = Build.PRODUCT.normalized()
        if (
            product.contains("sdk") ||
            product.contains("vbox") ||
            product.contains("emulator") ||
            product.contains("simulator")
        ) {
            return true
        }

        return readSystemProperty("ro.kernel.qemu") == "1"
    }

    fun isPaidAcquisitionUser(context: Context): Boolean =
        !isAutomatedEnvironment(context) &&
            !hasFakeLocationApp(context) &&
            !isCloakBlocked() &&
            !isReferrerBlocked() &&
            !isTestAd(context) &&
            !isTestDevice()

    private fun queryFakeLocationApp(context: Context): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchers = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(launcherIntent, 0)
            }
        }.getOrDefault(emptyList())
        return launchers.any { FakeLocationCatalog.contains(it.activityInfo?.packageName) }
    }

    private fun findMatch(values: Set<String>, tokens: List<String>): String? =
        tokens.firstOrNull { token ->
            token.isNotBlank() && values.any { value -> value.contains(token, ignoreCase = true) }
        }

    private fun decodeReferrer(referrer: String): String? = runCatching {
        URLDecoder.decode(referrer, Charsets.UTF_8.name())
    }.getOrNull()

    private fun String?.normalized(): String = orEmpty().lowercase(Locale.US)

    private fun readSystemProperty(name: String): String? = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("get", String::class.java)
            .invoke(null, name) as? String
    }.getOrNull()
}
