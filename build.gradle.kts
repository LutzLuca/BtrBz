plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
    java
}

fun getProp(name: String): String =
    findProperty(name) as? String ?: error("Missing Gradle property: $name")

group = getProp("maven_group")
version = getProp("mod_version") + "+" + getProp("mc_version")

base {
    archivesName.set(getProp("mod_id"))
}

loom {
    log4jConfigs.from(file("./log4j-dev.xml"))

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
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.project}")
    mappings("net.fabricmc:yarn:${getProp("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${getProp("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${getProp("fabric_version")}")
    testImplementation("net.fabricmc:fabric-loader-junit:${getProp("loader_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")

    implementation("net.hypixel:hypixel-api-core:4.4")
    include("net.hypixel:hypixel-api-core:4.4")

    implementation("net.hypixel:hypixel-api-transport-apache:4.4")
    include("net.hypixel:hypixel-api-transport-apache:4.4")

    implementation("io.vavr:vavr:0.10.7")
    include("io.vavr:vavr:0.10.7")

    implementation("com.google.code.gson:gson:2.8.9")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")

    modImplementation("dev.isxander:yet-another-config-lib:${getProp("yacl_version")}")
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
