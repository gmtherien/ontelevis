plugins {
    alias(libs.plugins.ontelevis.android.application)
    alias(libs.plugins.ontelevis.android.compose)
    alias(libs.plugins.ontelevis.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ontelevis"

    defaultConfig {
        applicationId = "app.ontelevis"
        // The release workflow passes both from the tag and the run number (-PversionName / -PversionCode);
        // local builds keep these.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
    }

    // Release signing comes from environment variables only (GitHub Actions secrets): the key is never
    // in the repository. Without them, release builds are left unsigned.
    val keystoreFile = System.getenv("ONTELEVIS_KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ONTELEVIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ONTELEVIS_KEY_ALIAS")
                keyPassword = System.getenv("ONTELEVIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))
    implementation(project(":feature:live"))
    implementation(project(":feature:guide"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:vod"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
