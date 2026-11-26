import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

fun readVersionCode(): Int {
    val envCode = System.getenv("QOBEAR_VERSION_CODE")?.toIntOrNull()
    return envCode ?: 100
}

fun readVersionName(): String {
    val envName = System.getenv("QOBEAR_VERSION_NAME")?.takeIf { it.isNotBlank() }
    return envName ?: "1.0.0"
}

android {
    namespace = "com.qorvhex.qobearvpn"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.qorvhex.qobearvpn"
        minSdk = 24
        targetSdk = 35
        versionCode = readVersionCode()
        versionName = readVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DCMAKE_POLICY_VERSION_MINIMUM=3.5"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("QOBEAR_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && File(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("QOBEAR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QOBEAR_KEY_ALIAS")
                keyPassword = System.getenv("QOBEAR_KEY_PASSWORD")
            } else {
                // Fallback to debug keystore so local/CI releases remain directly installable without setup.
                val debugSigning = signingConfigs.getByName("debug")
                storeFile = debugSigning.storeFile
                storePassword = debugSigning.storePassword
                keyAlias = debugSigning.keyAlias
                keyPassword = debugSigning.keyPassword
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = if (System.getenv("QOBEAR_EXTERNAL_SIGNING") == "true") {
                null
            } else {
                signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    applicationVariants.all {
        val variant = this
        val baseCode = variant.versionCode
        variant.outputs.map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }.forEach { output ->
            val abi = output.getFilter(com.android.build.OutputFile.ABI)
            val offset = when (abi) {
                "armeabi-v7a" -> 1_000_000
                "arm64-v8a" -> 2_000_000
                else -> 0
            }
            output.versionCodeOverride = baseCode + offset
            val abiLabel = abi ?: "universal"
            output.outputFileName = "QobearVPN-v${variant.versionName}-$abiLabel-${variant.buildType.name}.apk"
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.material)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}
