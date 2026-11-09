import app.ontelevis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
            // Never taken from the build cache: an empty KSP output stored there was restored later and
            // Hilt lost the ViewModels. See gradle.properties for the other two KSP workarounds.
            tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
                outputs.cacheIf { false }
            }
        }
    }
}
