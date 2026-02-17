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
        // GeckoView Maven 仓库
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
        }
    }
}

rootProject.name = "course_schedule_for_CHD_v0.0.2"
include(":app")
 