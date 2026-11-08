plugins {
    alias(libs.plugins.ontelevis.android.library)
    alias(libs.plugins.ontelevis.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "app.ontelevis.core.data"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:parser"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
}
