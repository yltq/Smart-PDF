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

    fun isPaidAcquisitionUser(context: Context): Boolean {
        val result =  !isAutomatedEnvironment(context) &&
                !hasFakeLocationApp(context) &&
                !isCloakBlocked() &&
                !isReferrerBlocked() &&
                !isTestAd(context) &&
                !isGoogleOrEmulator()
        return result
    }


    private fun isGoogleOrEmulator(): Boolean {
        val genericIdentity = Build.BRAND.startsWith("generic") &&
            Build.DEVICE.startsWith("generic")
        if (genericIdentity) return true

        if (Build.FINGERPRINT.startsWithEither("generic", "unknown")) return true
        if (Build.HARDWARE.containsEither("goldfish", "ranchu")) return true

        val virtualModel = Build.MODEL.containsEither(
            "google_sdk",
            "Emulator",
            "Android SDK built for x86"
        )
        if (virtualModel || Build.MANUFACTURER.contains("Genymotion")) return true

        return Build.PRODUCT.containsEither("sdk", "vbox86p", "emulator", "simulator")
    }

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

    private fun String.startsWithEither(vararg prefixes: String): Boolean =
        prefixes.any(::startsWith)

    private fun String.containsEither(vararg markers: String): Boolean =
        markers.any(::contains)
}
