pluginManagement {
    repositories {
        google()
        maven {
            name = "GoogleMavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            name = "GoogleMavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

rootProject.name = "JoiPlay Shortcut Generator"
include(":app")
