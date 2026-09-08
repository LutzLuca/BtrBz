plugins {
    id("dev.kikugie.stonecutter")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0" apply false
    id("com.diffplug.spotless") version "8.9.0" apply false
    checkstyle
}

stonecutter active "26.1"

repositories {
    mavenCentral()
}

checkstyle {
    toolVersion = "13.9.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

val checkstyleJava by tasks.registering(Checkstyle::class) {
    group = "verification"
    description = "Checks the shared Java sources against the repository style."
    source(fileTree("src") {
        include("main/java/**/*.java", "test/java/**/*.java")
    })
    classpath = files()

    reports {
        xml.required = true
        html.required = true
    }
}

afterEvaluate {
    pluginManager.apply("com.diffplug.spotless")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        encoding = Charsets.UTF_8

        java {
            target(fileTree("src") {
                include("main/java/**/*.java", "test/java/**/*.java")
            })

            eclipse("4.40").configFile(file("config/formatting/eclipse-java-formatter.xml"))
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.named("check") {
        dependsOn(checkstyleJava)
        dependsOn(stonecutter.versions.map { ":${it.project}:check" })
    }

    tasks.named("build") {
        setDependsOn(stonecutter.versions.map { versionProject ->
            ":${versionProject.project}:build"
        })
        dependsOn(tasks.named("check"))
    }
}

tasks.register("releaseMod") {
    group = "publishing"
    description = "Publishes the mod to GitHub and Modrinth"

    dependsOn(stonecutter.versions.map { ":${it.project}:publishMods" })
}
