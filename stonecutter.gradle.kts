plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.10"

tasks.register("releaseMod") {
    group = "publishing"
    description = "Releases the mod to all providers specified inside the `publishMods` task"

    stonecutter.versions.forEach { versionProject ->
        val sub = project(":${versionProject.project}")
        dependsOn(sub.tasks.named("publishMods"))
    }
}
