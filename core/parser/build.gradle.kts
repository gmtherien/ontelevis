plugins {
    alias(libs.plugins.ontelevis.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    // XmlPullParser API only: Android provides the implementation at runtime.
    compileOnly(libs.kxml2)
    testImplementation(libs.kxml2)
}
