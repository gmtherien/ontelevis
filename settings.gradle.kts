pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ontelevis"

include(":app")
include(":core:model")
include(":core:parser")
include(":core:data")
include(":core:player")
include(":core:designsystem")
include(":feature:live")
include(":feature:guide")
include(":feature:settings")
include(":feature:vod")
