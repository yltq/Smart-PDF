import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("stringfog")
}

val appConfig = Properties().apply {
    rootProject.file("app-config.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun configValue(key: String): String = appConfig.getProperty(key, "").trim()

fun configString(key: String): String = configValue(key)
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .let { "\"$it\"" }

stringfog {
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.zunelo.pdfreader"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.zunelo.pdfreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "ZuneloPDF-${versionName}-${versionCode}")
    }

    signingConfigs {
        create("release") {
            configValue("RELEASE_STORE_FILE").takeIf(String::isNotBlank)?.let {
                storeFile = file(it)
            }
            storePassword = configValue("RELEASE_STORE_PASSWORD")
            keyAlias = configValue("RELEASE_KEY_ALIAS")
            keyPassword = configValue("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "APPSFLYER_DEV_KEY", configString("DEBUG_APPSFLYER_DEV_KEY"))
            buildConfigField("String", "TBA_ENDPOINT", configString("DEBUG_TBA_ENDPOINT"))
            buildConfigField("String", "CLOAK_ENDPOINT", configString("DEBUG_CLOAK_ENDPOINT"))
            buildConfigField("String", "PRIVACY_URL", configString("DEBUG_PRIVACY_URL"))
            manifestPlaceholders["admobAppId"] = configValue("DEBUG_ADMOB_APP_ID")
            manifestPlaceholders["facebookAppId"] = configValue("DEBUG_FACEBOOK_APP_ID")
            manifestPlaceholders["facebookClientToken"] = configValue("DEBUG_FACEBOOK_CLIENT_TOKEN")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "APPSFLYER_DEV_KEY", configString("RELEASE_APPSFLYER_DEV_KEY"))
            buildConfigField("String", "TBA_ENDPOINT", configString("RELEASE_TBA_ENDPOINT"))
            buildConfigField("String", "CLOAK_ENDPOINT", configString("RELEASE_CLOAK_ENDPOINT"))
            buildConfigField("String", "PRIVACY_URL", configString("RELEASE_PRIVACY_URL"))
            manifestPlaceholders["admobAppId"] = configValue("RELEASE_ADMOB_APP_ID")
            manifestPlaceholders["facebookAppId"] = configValue("RELEASE_FACEBOOK_APP_ID")
            manifestPlaceholders["facebookClientToken"] = configValue("RELEASE_FACEBOOK_CLIENT_TOKEN")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(libs.androidx.ui.graphics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.pdfbox.android)
    implementation(libs.play.services.mlkit.document.scanner)
    implementation(libs.user.messaging.platform)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)
    implementation(libs.installreferrer)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
//    implementation("com.google.android.gms:play-services-time:16.0.1")
    implementation(libs.facebook.android.sdk)
    implementation(libs.appsflyer.android.sdk)
    implementation(libs.stringfog.xor)
    implementation("androidx.media:media:1.8.0")

    //admob
    implementation(libs.play.services.ads)
    // AdMob mediation adapters
    implementation("com.google.ads.mediation:applovin:13.6.4.2")
    implementation("com.google.ads.mediation:bidmachine:3.8.0.1")
    implementation("com.google.ads.mediation:vungle:7.7.8.1")
    implementation("com.google.ads.mediation:facebook:6.22.0.1")
    implementation("com.google.ads.mediation:mintegral:17.1.81.1")
    implementation("com.google.ads.mediation:moloco:4.12.0.1")
    implementation("com.google.ads.mediation:pangle:8.3.0.3.0")
    implementation("com.unity3d.ads:unity-ads:4.20.1")
    implementation("com.google.ads.mediation:unity:4.20.1.0")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
