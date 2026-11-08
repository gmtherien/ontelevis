plugins {
    alias(libs.plugins.ontelevis.android.library)
    alias(libs.plugins.ontelevis.android.compose)
}

android {
    namespace = "app.ontelevis.core.designsystem"
}

dependencies {
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.tv.material)
}
