plugins {
    kotlin("jvm") version "2.3.21"
}

group = "com.compdog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(22)
}

tasks.test {
    useJUnitPlatform()
}

val mainClasses = mapOf(
    "authlib-patcher" to "com.compdog.authlibpatcher.AuthlibPatcher",
    "server-patcher" to "com.compdog.authlibpatcher.ServerPatcher",
    "server-extractor" to "com.compdog.authlibpatcher.ServerExtractor"
)

val fatJars = mainClasses.map  { (jarName, mainClass) ->

    tasks.register<Jar>(jarName) {

        group = "build"
        description = "Builds $jarName"

        archiveBaseName.set(jarName)
        archiveVersion.set("")

        manifest {
            attributes["Main-Class"] = mainClass
        }

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // include compiled classes
        from(sourceSets.main.get().output)

        // include dependencies (fat jar)
        from({
            configurations.runtimeClasspath.get().map {
                if (it.isDirectory) it else zipTree(it)
            }
        })

        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(fatJars)
}