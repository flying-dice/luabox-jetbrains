import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        marketplace()
    }
}

dependencies {
    intellijPlatform {
        create(
            IntelliJPlatformType.fromCode(providers.gradleProperty("platformType").get()),
            providers.gradleProperty("platformVersion").get(),
        )

        // LSP client runtime, pulled from the JetBrains Marketplace.
        plugin("com.redhat.devtools.lsp4ij:${providers.gradleProperty("lsp4ijVersion").get()}")

        pluginVerifier()
    }

    implementation(kotlin("stdlib"))

    // BDD acceptance tests: Gherkin features run by the Cucumber JUnit Platform
    // engine. The lexer is a plain class, so these need no running IDE.
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("org.junit.platform:junit-platform-suite:1.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // The IDE classpath registers a JUnit launcher listener that touches JUnit4
    // API; provide it so the listener initialises instead of crashing the worker.
    testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    pluginVerification {
        ides {
            // Verify against the exact build target rather than `recommended()`,
            // which resolves to IDE versions not always published yet (it picked
            // an unavailable ideaIC:2025.3 on CI). Add more versions in the
            // since/until range here as they become available.
            ide(
                IntelliJPlatformType.fromCode(providers.gradleProperty("platformType").get()),
                providers.gradleProperty("platformVersion").get(),
            )
        }
    }
}
