import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "ai.rever.boss.plugin.dynamic"
// 0.1.0: side panel + 4 MCP tools. Calls bypass host MCP policy by design
// (see PlaygroundDispatcher); banner warns the operator.
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Auto-detect CI environment - in CI the api jar is downloaded into
// build/downloaded-deps by the test workflow; locally we expect a sibling
// checkout at ../boss-plugin-api with the same artifact.
val useLocalDependencies = System.getenv("CI") != "true"
val bossPluginApiPath = "../boss-plugin-api"
val bossPluginApiVersion = "1.0.93"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// In CI the test workflow downloads the API jar, but a `clean` task will
// wipe the directory before compileKotlin runs - so we mirror the workflow
// here as a build-time fallback that re-fetches the jar if it's missing.
val downloadBossPluginApi = tasks.register("downloadBossPluginApi") {
    description = "Downloads the boss-plugin-api jar used for compileOnly."
    val target = layout.buildDirectory.file("downloaded-deps/boss-plugin-api.jar")
    outputs.file(target)
    doLast {
        val outFile = target.get().asFile
        if (outFile.exists() && outFile.length() > 0) return@doLast
        outFile.parentFile.mkdirs()
        val url = "https://github.com/risa-labs-inc/boss-plugin-api/releases/download/v$bossPluginApiVersion/boss-plugin-api-$bossPluginApiVersion.jar"
        val proc = ProcessBuilder("curl", "-fsSL", "--retry", "3", "--retry-all-errors", "-o", outFile.absolutePath, url)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().forEachLine { println(it) }
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("curl failed with exit=$exit")
    }
}

dependencies {
    if (useLocalDependencies) {
        // Local development: use boss-plugin-api JAR from sibling repo
        compileOnly(files("$bossPluginApiPath/build/libs/boss-plugin-api-$bossPluginApiVersion.jar"))
    } else {
        // CI: use downloaded JAR
        compileOnly(files("build/downloaded-deps/boss-plugin-api.jar"))
        // Ensure the jar is on disk before compileKotlin runs. The test
        // workflow normally populates this directory, but a local `clean`
        // task will otherwise leave it empty.
        tasks.named("compileKotlin") {
            dependsOn(downloadBossPluginApi)
        }
    }

    // Compose dependencies
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)

    // Compose Icons (FeatherIcons)
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")

    // Decompose for ComponentContext
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // JSON parsing for tool args (McpToolArgs takes a Map<String, Any?>)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// Task to build plugin JAR with compiled classes only
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-mcp-tool-playground-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Implementation-Title" to "BOSS MCP Tool Playground Plugin",
            "Implementation-Version" to version,
            "Main-Class" to "ai.rever.boss.plugin.dynamic.playground.PlaygroundDynamicPlugin"
        )
    }

    // Include compiled classes
    from(sourceSets.main.get().output)

    // Include plugin manifest
    from("src/main/resources")
}

// Sync version from build.gradle.kts into plugin.json (single source of truth)
tasks.processResources {
    filesMatching("**/plugin.json") {
        filter { line ->
            line.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "\$version"""")
        }
    }
}

tasks.build {
    dependsOn("buildPluginJar")
}
