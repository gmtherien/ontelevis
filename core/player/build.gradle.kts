plugins {
    alias(libs.plugins.ontelevis.android.library)
    alias(libs.plugins.ontelevis.hilt)
}

android {
    namespace = "app.ontelevis.core.player"
}

dependencies {
    api(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // FFmpeg audio decoders (AC3 / E-AC3 / DTS...) for boxes without a licensed Dolby decoder.
    implementation(libs.jellyfin.media3.ffmpeg)
}
