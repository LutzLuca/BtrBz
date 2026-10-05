plugins { `java-library` }
group = "com.github.lutzluca"
version = "1.0.0"
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
