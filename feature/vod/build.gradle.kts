plugins {
    alias(libs.plugins.ontelevis.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ontelevis.feature.vod"
}

dependencies {
    implementation(project(":core:player"))
    implementation(libs.media3.ui.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
