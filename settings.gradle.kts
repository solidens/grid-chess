pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // chesslib publishes through JitPack, not Maven Central.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.bhlangonijr") }
        }
    }
}

rootProject.name = "GridChess"
include(":app")
