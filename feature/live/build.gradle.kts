plugins {
    alias(libs.plugins.ontelevis.android.feature)
}

android {
    namespace = "app.ontelevis.feature.live"
}

dependencies {
    implementation(project(":core:player"))
    implementation(libs.media3.ui.compose)
}
