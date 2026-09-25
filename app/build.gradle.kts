plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.phonosassist"
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "com.phonosassist"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // sherpa-onnx prebuilts are arm64 only; ship a single ABI.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        create("release") {
            // Credentials live outside the repo: env vars or entries in the
            // local `~/.gradle/gradle.properties`
            // (PHONOSASSIST_STORE_FILE / PHONOSASSIST_STORE_PASSWORD /
            // PHONOSASSIST_KEY_ALIAS / PHONOSASSIST_KEY_PASSWORD).
            // Never commit a keystore or its passwords.
            val prop = { name: String -> System.getenv(name) ?: project.findProperty(name)?.toString() }
            storeFile = prop("PHONOSASSIST_STORE_FILE")?.let(::file)
                ?: file("${System.getProperty("user.home")}/.android/phonosassist-release.jks")
            storePassword = prop("PHONOSASSIST_STORE_PASSWORD")
            keyAlias = prop("PHONOSASSIST_KEY_ALIAS") ?: "phonosassist"
            keyPassword = prop("PHONOSASSIST_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed only when the release keystore + passwords are present
            // (local `~/.gradle/gradle.properties` or env vars). Public CI
            // skips signing and just validates the build compiles.
            val prop = { name: String -> System.getenv(name) ?: project.findProperty(name)?.toString() }
            val store = prop("PHONOSASSIST_STORE_FILE")?.let(::file)
                ?: file("${System.getProperty("user.home")}/.android/phonosassist-release.jks")
            val storePw = prop("PHONOSASSIST_STORE_PASSWORD")
            if (store.exists() && !storePw.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.onnxruntime)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
