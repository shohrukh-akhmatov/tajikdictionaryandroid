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
        // Yandex Mobile Ads 8.7+ is published only here (Maven Central stops at 8.6.0).
        maven {
            url = uri("https://ads.maven.yandex.net/")
            content { includeGroup("com.yandex.android") }
        }
    }
}

rootProject.name = "TajikDictionary"
include(":app")
