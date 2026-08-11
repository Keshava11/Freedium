import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing is read from keystore.properties, which is git-ignored and never
 * committed. Without it the release variant still builds, just unsigned - see the README
 * note in proguard-rules.pro for the keytool command to create one.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
/**
 * Resolve against the project root, not this module. `file()` inside app/build.gradle.kts
 * is relative to app/, so a plain "freedium-release.jks" in keystore.properties would be
 * looked for at app/freedium-release.jks. rootProject.file() resolves it next to
 * keystore.properties itself, and still honours an absolute path if one is given.
 */
val releaseKeystore = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it) }

val hasReleaseSigning = releaseKeystore?.exists() == true

if (releaseKeystore != null && !hasReleaseSigning) {
    // Configured but pointing at nothing - say so now, with the path actually tried,
    // rather than letting it quietly emit an unsigned APK that a device will refuse.
    logger.warn(
        "Freedium: keystore.properties names a storeFile that does not exist:\n" +
                "  ${releaseKeystore.absolutePath}\n" +
                "  Release builds will be UNSIGNED and will not install."
    )
}

android {
    namespace = "com.ravi.freedium"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ravi.freedium"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimise and obfuscate. Also strips the debug-only logging,
            // because FreediumLog guards on the BuildConfig.DEBUG compile-time constant.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Keep the debug build readable - obfuscating it would make the on-device
            // investigation this app exists for much harder.
            //
            // Deliberately no applicationIdSuffix: changing the package id would orphan
            // the notification-listener grant and the medium.com "Open by default"
            // approval, both of which are granted per package and were set up by hand.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // Needed for BuildConfig.DEBUG, which gates all the verbose logging.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    ksp(libs.androidx.room.compiler)

    // Encrypted database at rest; the passphrase itself lives behind the Android Keystore.
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    // Weekly retention sweep
    implementation(libs.androidx.work)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
