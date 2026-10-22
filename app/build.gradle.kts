import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val paymentProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
// Grade 7 has its own Bazaar application and RSA key. Never reuse the Grade 9 key.
val grade7BazaarKey = providers.gradleProperty("BAZAAR_RSA_PUBLIC_KEY")
    .orElse(providers.environmentVariable("BAZAAR_RSA_PUBLIC_KEY"))
    .orElse(paymentProperties.getProperty("BAZAAR_RSA_PUBLIC_KEY", ""))
    .get().replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.oloomyar.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.oloomyar.app.grade7"
        minSdk = 23
        targetSdk = 37
        versionCode = 13
        versionName = "0.12.0-grade7-1405"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "WORKBOOK_GRADE", "7")
        buildConfigField("String", "BAZAAR_RSA_PUBLIC_KEY", "\"$grade7BazaarKey\"")
        buildConfigField("boolean", "REVIEW_MODE", "false")
    }

    // Public, review-only key: make debug updates installable across CI runners.
    // Production/release builds must use a separately protected signing key.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("qa/signing/grade7-review.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Source originals are retained for provenance. Ship reviewed WebP derivatives
    // and one copy of each PDF, never duplicate PNG crops.
    androidResources { ignoreAssetsPattern = "*.png:*.tmp:.*:!CVS:!thumbs.db:!picasa.ini:!*~" }
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "REVIEW_MODE", "true") }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
