import org.gradle.kotlin.dsl.register
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    `maven-publish` // Maven Publishing for GitHub Packages
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").getOrElse("")
val publishToken = providers.gradleProperty("token").getOrElse("")
val vversion = version.toString()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        // Extract the description from README.md so the description can be maintained in one place
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            // We expect a top-level heading like: '# Maven Consistency Enforcer (MCE)'
            // and stop at the next level-2 heading (e.g. '## Screenshots').
            val lines = it.lines().map { line -> Regex("^[ ]*").replaceFirst(line, "") }
            val startHeaderRegex = Regex("^#\\s+Maven Consistency Enforcer.*", RegexOption.IGNORE_CASE)
            val startIndex = lines.indexOfFirst { startHeaderRegex.matches(it) }
            val endIndex = lines.indexOfFirst { it.startsWith("[//]: # (plugin-description-end)") && it.trim().isNotEmpty() }

            if (startIndex == -1) {
                throw GradleException("README section 'Maven Consistency Enforcer' not found. Please add a top-level heading '# Maven Consistency Enforcer' to README.md or update the build script.")
            }

            val contentLines = if (endIndex != -1 && endIndex > startIndex) {
                lines.subList(startIndex + 1, endIndex)
            } else {
                lines.subList(startIndex + 1, lines.size)
            }

            contentLines.joinToString("\n").let { markdown -> markdownToHTML(markdown) }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = with(changelog) {
            renderItem(
                (getOrNull(vversion) ?: getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML,
            )
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            //            untilBuild = providers.gradleProperty("pluginUntilBuild").getOrElse("")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        //        token = publishToken
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("channels").orElse("Stable").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "Stable" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl").getOrElse("").replace(".git", "")
    versionPrefix = ""
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Finncu/maven-consistency-enforcer")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            groupId = providers.gradleProperty("pluginGroup").get()
            artifactId = providers.gradleProperty("pluginId").get()
            version = project.version.toString()

            // Attach the built plugin ZIP as artifact
            artifact(tasks.buildPlugin.get().archiveFile) {
                classifier = "plugin"
                artifactId = providers.gradleProperty("pluginId").get()
                groupId = providers.gradleProperty("pluginGroup").get()
                version = providers.gradleProperty("pluginVersion").get()
                extension = "zip"
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        token = publishToken
        dependsOn(patchChangelog)
    }

    publish {
        dependsOn(buildPlugin)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
