plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1"

tasks.register("releaseMod") {
    group = "publishing"
    description = "Releases the mod to all providers specified inside the `publishMods` task"

    stonecutter.versions.forEach { versionProject ->
        val sub = project(":${versionProject.project}")
        dependsOn(sub.tasks.named("publishMods"))
    }
}
