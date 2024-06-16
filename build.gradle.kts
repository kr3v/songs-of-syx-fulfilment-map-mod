plugins {
    kotlin("jvm") version "1.9.23"
}

group = "kr3v"
version = "0.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(files("libs/songs-of-syx/SongsOfSyx.jar"))
    implementation("io.ktor:ktor-server-cio:2.3.11")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}

// fat jar task
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("fulfilment-provider")
    archiveClassifier.set("all")
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") && !it.name.startsWith("SongsOfSyx") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}