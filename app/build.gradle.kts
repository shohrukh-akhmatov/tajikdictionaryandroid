import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Ad config value: local.properties (gitignored, per machine) wins over Config/AdUnits.properties
 * (committed; ad-unit IDs are public, they ship inside the APK). Empty when neither has it.
 */
fun adSetting(name: String): String {
    for (file in listOf(rootProject.file("local.properties"), rootProject.file("Config/AdUnits.properties"))) {
        if (!file.exists()) continue
        val props = Properties().apply { file.inputStream().use { load(it) } }
        props.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return ""
}

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Official test/demo units: safe to use in every debug build and every placement.
val googleTestAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestUnits = mapOf(
    "BANNER" to "ca-app-pub-3940256099942544/9214589741",
    "ENTRY_BANNER" to "ca-app-pub-3940256099942544/9214589741",
    "INTERSTITIAL" to "ca-app-pub-3940256099942544/1033173712",
)
val yandexDemoUnits = mapOf(
    "BANNER" to "demo-banner-yandex",
    "ENTRY_BANNER" to "demo-banner-yandex",
    "INTERSTITIAL" to "demo-interstitial-yandex",
)
val adUnitKinds = googleTestUnits.keys
val admobAppId = adSetting("ADMOB_APP_ID")
val admobUnits = adUnitKinds.associateWith { adSetting("ADMOB_${it}_ID") }
val yandexUnits = adUnitKinds.associateWith { adSetting("YANDEX_${it}_ID") }
val admobTestDeviceIds = adSetting("ADMOB_TEST_DEVICE_IDS")
// A release with any production ID missing ships with ads off rather than with test ads.
val productionAdsConfigured = (listOf(admobAppId) + admobUnits.values + yandexUnits.values).all(String::isNotBlank)

android {
    namespace = "tj.tajikdictionary"
    compileSdk = 37

    defaultConfig {
        applicationId = "tj.tajikdictionary"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = googleTestAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_APP_ID", quoted(googleTestAppId))
            buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", quoted(admobTestDeviceIds))
            adUnitKinds.forEach { kind ->
                buildConfigField("String", "ADMOB_${kind}_ID", quoted(googleTestUnits.getValue(kind)))
                buildConfigField("String", "YANDEX_${kind}_ID", quoted(yandexDemoUnits.getValue(kind)))
            }
        }
        release {
            // The manifest needs a well-formed app ID even when ads are off, or the SDK crashes at startup.
            manifestPlaceholders["admobAppId"] = admobAppId.ifBlank { googleTestAppId }
            buildConfigField("boolean", "ADS_ENABLED", productionAdsConfigured.toString())
            buildConfigField("String", "ADMOB_APP_ID", quoted(admobAppId))
            buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", quoted(""))
            adUnitKinds.forEach { kind ->
                buildConfigField("String", "ADMOB_${kind}_ID", quoted(admobUnits.getValue(kind)))
                buildConfigField("String", "YANDEX_${kind}_ID", quoted(yandexUnits.getValue(kind)))
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // The bundled dictionary is copied to internal storage on first launch; keep it uncompressed.
        noCompress += "sqlite"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    // QR code of the app link on share cards.
    implementation(libs.zxing.core)
    // Ads: UMP consent, then a Yandex -> Google waterfall (see ads/WaterfallBanner.kt).
    implementation(libs.yandex.mobileads)
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)

    testImplementation(libs.junit)
    // Runs the real search SQL against the bundled dictionary in plain JVM tests.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.junit)
}
