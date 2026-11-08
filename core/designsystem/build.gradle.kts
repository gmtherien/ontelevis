plugins {
    alias(libs.plugins.ontelevis.android.library)
    alias(libs.plugins.ontelevis.android.compose)
}

android {
    namespace = "app.ontelevis.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
