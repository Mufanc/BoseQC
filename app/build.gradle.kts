plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val cfgReleaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE").orNull
val cfgReleaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val cfgReleaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val cfgReleaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull

android {
    namespace = "xyz.mufanc.bqc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "xyz.mufanc.bqc"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = cfgReleaseStoreFile?.let { file(it) }
            storePassword = cfgReleaseStorePassword
            keyAlias = cfgReleaseKeyAlias
            keyPassword = cfgReleaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
