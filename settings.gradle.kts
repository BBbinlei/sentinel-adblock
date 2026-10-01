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

rootProject.name = "Sentinel"
include(":app", ":data", ":core-rules", ":guard",
    ":engine-vpn", ":engine-a11y", ":engine-notify", ":engine-system",
    ":testing:rule-regression", ":testing:device-integration")
