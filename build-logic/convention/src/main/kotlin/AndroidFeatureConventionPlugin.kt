import app.ontelevis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A `:feature:*` module: Android library + Compose + Hilt + the design system. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("ontelevis.android.library")
            pluginManager.apply("ontelevis.android.compose")
            pluginManager.apply("ontelevis.hilt")
            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:model"))
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                add("implementation", project(":core:data"))
            }
        }
    }
}
