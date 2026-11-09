plugins {
    alias(libs.plugins.ontelevis.android.feature)
}

android {
    namespace = "app.ontelevis.feature.settings"
}

dependencies {
    implementation(project(":core:playerui"))
    implementation(libs.androidx.activity.compose)
}
