plugins {
    id("dev.kikugie.stonecutter")
    id("com.diffplug.spotless") version "8.7.0"
}

stonecutter active "26.1"

repositories {
    mavenCentral()
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    java {
        target("src/**/*.java")
        eclipse().configFile(rootProject.file("formatting.xml"))
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
