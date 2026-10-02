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
        // usb-serial-for-android (USB TPMS receiver).
        maven("https://jitpack.io")
    }
}

rootProject.name = "LibreHU-Launcher"
include(":app")
