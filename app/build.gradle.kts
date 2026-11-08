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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
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
