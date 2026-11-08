plugins {
    `kotlin-dsl`
}

group = "app.ontelevis.buildlogic"

dependencies {
    // Only the Android Gradle Plugin API is compiled against. Every other plugin
    // (Kotlin, Compose, KSP, Hilt) is applied by id, so build-logic never reads
    // their classes and stays independent of their Kotlin metadata version.
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ontelevis.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "ontelevis.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "ontelevis.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "ontelevis.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "ontelevis.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "ontelevis.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
