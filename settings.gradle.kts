pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Linphone Maven repository hosting the Liblinphone Android SDK
        maven {
            url = uri("https://download.linphone.org/maven_repository/")
        }
    }
}

rootProject.name = "SipCallingApp"
include(":app")
