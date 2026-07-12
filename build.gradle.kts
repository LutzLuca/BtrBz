plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("me.modmuss50.mod-publish-plugin") version "2.0.1"
    java
}

fun getProp(name: String): String =
    rootProject.findProperty(name) as? String ?: findProperty(name) as? String
    ?: error("Missing Gradle property: $name")

val releaseType = getProp("release_type")
val versionSuffix = if (releaseType == "release") "" else "-$releaseType"

group = getProp("maven_group")
version = "${getProp("mod_version")}$versionSuffix+${getProp("mc_version")}"

base {
    archivesName.set(getProp("mod_id"))
}

loom {
    log4jConfigs.from(file("log4j-dev.xml"))

    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "../../run"
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.hypixel.net/repository/Hypixel/")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.terraformersmc.com/")
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.project}")
    implementation("net.fabricmc:fabric-loader:${getProp("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${getProp("fabric_version")}")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")

    implementation("net.hypixel:hypixel-api-core:4.4")
    include("net.hypixel:hypixel-api-core:4.4")

    implementation("net.hypixel:hypixel-api-transport-apache:4.4")
    include("net.hypixel:hypixel-api-transport-apache:4.4")

    include("org.apache.httpcomponents:httpclient:4.5.14")
    include("org.apache.httpcomponents:httpcore:4.4.16")
    include("commons-logging:commons-logging:1.2")

    implementation("io.vavr:vavr:0.10.7")
    include("io.vavr:vavr:0.10.7")

    implementation("com.google.code.gson:gson:2.14.0")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")

    implementation("dev.isxander:yet-another-config-lib:${getProp("yacl_version")}")

    compileOnly("com.terraformersmc:modmenu:${getProp("modmenu_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        inputs.property("mc_version", stonecutter.current.version)

        filesMatching("fabric.mod.json") {
            expand(getProperties())
            expand(mutableMapOf("version" to project.version))
        }
    }

    test {
        useJUnitPlatform()
    }
}

java {
    withSourcesJar()
}

publishMods {
    dryRun.set(false)

    file = tasks.jar.get().archiveFile
    changelog = rootProject.file("CHANGELOG_LATEST.md").takeIf { it.exists() }?.readText()
        ?: "No changelog provided"

    type = when (releaseType) {
        "alpha" -> ALPHA
        "beta" -> BETA
        else -> STABLE
    }
    modLoaders.add("fabric")

    displayName =
        "BtrBz v${getProp("mod_version")}$versionSuffix for ${getProp("mc_version")}"
    version = "${getProp("mod_version")}$versionSuffix+${stonecutter.current.version}"

    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = getProp("github_repo")
        commitish = "master"
    }

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "IzWPcaNg"
        minecraftVersionRange {
            start.set(stonecutter.current.version)
            end.set(getProp("modrinth_minecraft_version_end"))
        }

        projectDescription = rootProject.file("README.md").readText()

        requires("fabric-api", "yacl")
        optional("modmenu")
    }
}
