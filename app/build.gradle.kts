import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signing credentials live outside the repository. When they are absent -- a
// fresh clone, or CI -- the release build still runs and simply comes out
// unsigned, rather than failing.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.gridchess"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gridchess"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ONNX Runtime ships x86/x86_64 for emulators and arm for devices.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        // Where online play lives, and where a key is bought. Set in
        // gradle.properties; an empty server URL hides online play altogether.
        buildConfigField("String", "ONLINE_URL", "\"${providers.gradleProperty("grid.online.url").getOrElse("")}\"")
        buildConfigField("String", "GUMROAD_URL", "\"${providers.gradleProperty("grid.gumroad.url").getOrElse("")}\"")
    }

    signingConfigs {
        create("release") {
            val store = keystoreProperties.getProperty("storeFile")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // The emulator's name for the laptop it runs on, where the server is started by hand.
            buildConfigField("String", "ONLINE_URL", "\"ws://10.0.2.2:8080/play\"")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
                .takeIf { keystoreProperties.getProperty("storeFile") != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // The .onnx nets are already compressed; keep them uncompressed so
        // ONNX Runtime can mmap them straight out of the APK.
        noCompress += "onnx"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // ONNX Runtime carries a ~17 MB native library per ABI, which is most of the
    // APK. Splitting means a device downloads one of them instead of three.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.chesslib)
    implementation(libs.onnxruntime.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
