plugins {
    alias(libs.plugins.ontelevis.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ontelevis.feature.home"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
