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
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description =
            "Lua support for JetBrains IDEs via the luabox language server " +
            "(diagnostics, hover, completion, go-to-definition, document symbols, " +
            "formatting, semantic highlighting) plus a bundled .lua base-highlighting " +
            "layer."

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        vendor {
            name = "flying-dice"
            url = "https://github.com/flying-dice/luabox"
        }
    }

    pluginVerification {
        ides {
            // Verify against the exact build target rather than `recommended()`,
            // which resolves to IDE versions not always published yet.
            ide(
                IntelliJPlatformType.fromCode(providers.gradleProperty("platformType").get()),
                providers.gradleProperty("platformVersion").get(),
            )
        }
    }

    // No publishing/signing block: this plugin is never auto-published. Releases
    // build the zip and attach it to a GitHub Release; uploading to the JetBrains
    // Marketplace is a deliberate manual step done with the downloaded artifact.
}
