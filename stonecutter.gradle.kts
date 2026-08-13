plugins {
    id("dev.kikugie.stonecutter")
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
    }

    tasks.named("build") {
        setDependsOn(stonecutter.versions.map { versionProject ->
            ":${versionProject.project}:build"
        })
    }
}

tasks.register("releaseMod") {
    group = "publishing"
    description = "Releases the mod to all providers specified inside the `publishMods` task"

    stonecutter.versions.forEach { versionProject ->
        val sub = project(":${versionProject.project}")
        dependsOn(sub.tasks.named("publishMods"))
    }
}
