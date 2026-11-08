package app.ontelevis.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

internal val JAVA_VERSION = JavaVersion.VERSION_17

internal fun Lint.configureLint() {
    abortOnError = true
    checkDependencies = true
    // No user-visible string outside strings.xml, and every string translated.
    error += setOf("HardcodedText", "MissingTranslation", "UnsafeOptInUsageError")
}

internal fun ApplicationExtension.configureAndroid(project: Project) {
    compileSdk = project.libs.int("compileSdk")
    defaultConfig {
        minSdk = project.libs.int("minSdk")
        targetSdk = project.libs.int("targetSdk")
    }
    compileOptions {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    lint { configureLint() }
}

internal fun LibraryExtension.configureAndroid(project: Project) {
    compileSdk = project.libs.int("compileSdk")
    defaultConfig {
        minSdk = project.libs.int("minSdk")
    }
    compileOptions {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    lint { configureLint() }
}
