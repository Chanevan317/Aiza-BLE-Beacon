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

rootProject.name = "aiza_app"

// Android app (UI, navigation, DI wiring)
include(":app")

// Pure Kotlin/JVM modules — NO Android imports (SRS C-3, NFR-MNT-01)
include(":core:model")      // timestamped sample + result types shared by everything
include(":core:direction")  // hot/cold, PDR, particle filter, 120° wedge
include(":core:logformat")  // trial-log CSV schema: write on phone, read in offline tools

// Android platform layer: turns BLE + sensors into core:model samples
include(":platform:ble")
include(":platform:sensors")

// Android data layer: tags, settings, trial logging + export
include(":data:tags")
include(":data:trial")

// Offline tools (run on a computer)
include(":tools:replay")
