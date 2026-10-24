import java.util.Properties
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val grade7PaymentConfigurationFile = rootProject.file("config/bazaar-grade7.properties")
val committedPaymentProperties = Properties().apply {
    grade7PaymentConfigurationFile.inputStream().use { load(it) }
}
// Grade 7 has its own Bazaar application and RSA key. Never reuse the Grade 9 key.
// One tracked public configuration is authoritative after every Pull. Legacy
// local.properties, environment and -P values cannot silently replace this key.
val grade7BazaarKey = committedPaymentProperties
    .getProperty("GRADE7_BAZAAR_RSA_PUBLIC_KEY", "").filterNot { it.isWhitespace() }
val grade7BazaarKeyFingerprint = committedPaymentProperties
    .getProperty("GRADE7_BAZAAR_RSA_PUBLIC_KEY_SHA256", "").trim()

val grade7BazaarKeyLiteral = grade7BazaarKey.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.oloomyar.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.oloomyar.app.grade7"
        minSdk = 23
        targetSdk = 37
        versionCode = 19
        versionName = "0.15.2-grade7-1405"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "WORKBOOK_GRADE", "7")
        buildConfigField("String", "BAZAAR_RSA_PUBLIC_KEY", "\"$grade7BazaarKeyLiteral\"")
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
            buildConfigField("boolean", "REVIEW_MODE", "false")
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

// Validate the tracked key and its owner-approved fingerprint before release builds.
val verifyBazaarReleaseConfiguration by tasks.registering {
    inputs.file(grade7PaymentConfigurationFile)
    doLast {
        val key = runCatching {
            KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(grade7BazaarKey)))
        }.getOrNull()
        require(key is RSAPublicKey && key.modulus.bitLength() >= 1024) {
            "Invalid Grade 7 Bazaar RSA key in config/bazaar-grade7.properties. Restore the owner's public configuration from main."
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(key.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(fingerprint == grade7BazaarKeyFingerprint) {
            "Grade 7 Bazaar RSA fingerprint mismatch in config/bazaar-grade7.properties. Restore the owner's approved key and fingerprint."
        }
        require(android.defaultConfig.applicationId == "com.oloomyar.app.grade7") {
            "The Grade 7 Bazaar payment key must only be used with com.oloomyar.app.grade7."
        }
        require(android.buildTypes.getByName("release").buildConfigFields["REVIEW_MODE"]?.value == "false") {
            "Bazaar release builds must keep REVIEW_MODE=false."
        }
        logger.lifecycle("Grade 7 Bazaar configuration: PASS")
        logger.lifecycle("Public key source: config/bazaar-grade7.properties (local RSA overrides are ignored)")
        logger.lifecycle("Application ID: {}", android.defaultConfig.applicationId)
        logger.lifecycle("RSA public key: {} bits; SHA-256: {}", key.modulus.bitLength(), fingerprint)
        logger.lifecycle("Release REVIEW_MODE=false; chapter 1 is free, chapters 2-15 require verified ownership.")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyBazaarReleaseConfiguration)
}
tasks.register("prepareBazaarRelease") {
    dependsOn(verifyBazaarReleaseConfiguration, "bundleRelease")
}
tasks.register("prepareBazaarReleaseApk") {
    dependsOn(verifyBazaarReleaseConfiguration, "assembleRelease")
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
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
