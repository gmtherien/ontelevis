import app.ontelevis.buildlogic.libs
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.dependencies

/** Adds Jetpack Compose to an Android application or library module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // Models from :core:model are declared stable (compose_stability.conf at the repo root).
            // Set through Gradle's own types: build-logic does not compile against the Compose plugin.
            val composeCompiler = extensions.getByName("composeCompiler")

            @Suppress("UNCHECKED_CAST")
            val stabilityFiles =
                composeCompiler.javaClass
                    .getMethod("getStabilityConfigurationFiles")
                    .invoke(composeCompiler) as ListProperty<RegularFile>
            stabilityFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
            pluginManager.withPlugin("com.android.application") {
                extensions.getByType(ApplicationExtension::class.java).buildFeatures.compose = true
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.getByType(LibraryExtension::class.java).buildFeatures.compose = true
            }
            dependencies {
                val bom = platform(libs.findLibrary("compose-bom").get())
                add("implementation", bom)
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            }
        }
    }
}
