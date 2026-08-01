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
        maven { url = java.net.URI("https://repo.eclipse.org/content/repositories/paho-releases/") }
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "DeviceApp"

include(":app")

// Core Modules
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:network")
include(":core:security")
include(":core:hardware-api")
include(":core:hardware-drivers")
include(":core:payment")
include(":core:sync")
include(":core:location")
include(":core:devicemanager")

// Feature Modules
include(":feature:validator")
include(":feature:diagnostic")
include(":feature:settings")
