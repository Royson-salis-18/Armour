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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "armor"

// :core is plain Kotlin/JVM and holds every safety-critical rule, so it can be built and
// tested anywhere. Pass -Parmor.coreOnly=true on machines without the Android SDK / Google Maven.
include(":core")
if (providers.gradleProperty("armor.coreOnly").orNull != "true") {
    include(":app")
}
