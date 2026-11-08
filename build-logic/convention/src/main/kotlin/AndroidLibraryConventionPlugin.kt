import app.ontelevis.buildlogic.configureAndroid
import app.ontelevis.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(target)
            }
            dependencies {
                add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
                add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
                add("testImplementation", libs.findLibrary("junit-jupiter").get())
                add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
            }
            tasks.withType<Test>().configureEach { useJUnitPlatform() }
        }
    }
}
