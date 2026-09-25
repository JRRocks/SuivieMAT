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
    }
}

// Nom du projet Gradle (IDE, artefacts de build) : purement cosmetique, sans effet sur
// l'applicationId. Voir app/build.gradle.kts pour ce qui distingue reellement les deux
// applications sur un meme telephone.
rootProject.name = "SuivieMAT"
include(":app")
