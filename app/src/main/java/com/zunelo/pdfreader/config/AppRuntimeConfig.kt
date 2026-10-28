package com.zunelo.pdfreader.config

import com.zunelo.pdfreader.BuildConfig

/** Build-variant values injected from app-config.properties. */
object AppRuntimeConfig {
    val appsFlyerDevKey: String = BuildConfig.APPSFLYER_DEV_KEY.trim()
    val tbaEndpoint: String = BuildConfig.TBA_ENDPOINT.trim()
    val cloakEndpoint: String = BuildConfig.CLOAK_ENDPOINT.trim()
    val privacyUrl: String = BuildConfig.PRIVACY_URL.trim()
}
