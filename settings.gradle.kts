pluginManagement {
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

rootProject.name = "zhuyin-voice-ime"

include(":core:contracts")
include(":core:dictionary")
include(":engine:zhuyin")
include(":feature:voice")
include(":voice-sandbox")
include(":app")
