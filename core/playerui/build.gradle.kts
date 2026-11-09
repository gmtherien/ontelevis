plugins {
    alias(libs.plugins.ontelevis.android.library)
    alias(libs.plugins.ontelevis.android.compose)
    alias(libs.plugins.ontelevis.hilt)
}

android {
    namespace = "app.ontelevis.core.playerui"
}

// Player UI shared by the live and the movie players: video with picture format and subtitles,
// the options menu (tracks, format, stats, sleep timer) and the choices remembered per title.
dependencies {
    api(project(":core:player"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
}
