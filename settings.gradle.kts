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
        // Maven repository for Supabase
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Optimization-Engine"
include(":app")
