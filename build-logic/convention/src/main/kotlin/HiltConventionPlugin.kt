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
            // KSP2 bug: when only a dependency module changed, the task re-ran, generated nothing and
            // wiped its previous output, so Hilt lost the module's ViewModels ("Cannot create an
            // instance of ...ViewModel" at runtime). Starting from empty caches makes every run a
            // full one; the modules are small, so it costs little. Never taken from the build cache:
            // a forced rerun (--rerun-tasks) once stored an empty output there, and later builds
            // (even after clean) restored it and failed with Hilt MissingBinding errors.
            // Only this task's own variant is cleared ("kspDebugKotlin" -> debug): clearing the whole
            // generated/ksp folder let a test KSP task wipe the code the app's KSP task had just made.
            tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
                val variant = name.removePrefix("ksp").removeSuffix("Kotlin").replaceFirstChar { it.lowercase() }
                val kspState =
                    listOf(
                        layout.buildDirectory.dir("kspCaches/$variant"),
                        layout.buildDirectory.dir("generated/ksp/$variant"),
                    )
                outputs.cacheIf { false }
                doFirst { kspState.forEach { it.get().asFile.deleteRecursively() } }
            }
        }
    }
}
