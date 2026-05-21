package com.compdog.authlibpatcher

import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import kotlin.system.exitProcess

object ServerExtractor {

    @JvmStatic
    fun main(args: Array<String>) {

        if (args.size < 3) {
            println(
                "Usage: <server.jar> <group:artifact> <output-directory>"
            )
            exitProcess(1)
        }

        val serverJar = File(args[0])
        val artifactIdWithoutVersion = args[1]
        val outputDir = File(args[2])

        if (!serverJar.exists()) {
            System.err.println("Server jar does not exist")
            exitProcess(2)
        }

        outputDir.mkdirs()

        JarFile(serverJar).use { jar ->

            val librariesEntry = jar.getJarEntry("META-INF/libraries.list")

            if (librariesEntry == null) {
                System.err.println("META-INF/libraries.list not found")
                exitProcess(3)
            }

            val librariesText =
                jar.getInputStream(librariesEntry)
                    .bufferedReader()
                    .readText()

            val lines = librariesText.lines()

            for (line in lines) {

                if (line.isBlank()) continue

                val parts = line.split("\t")

                if (parts.size < 3) continue

                val hash = parts[0]
                val fullArtifactId = parts[1]
                val path = parts[2]

                /*
                 * Match:
                 * com.mojang:authlib
                 * against:
                 * com.mojang:authlib:7.0.63
                 */

                if (!fullArtifactId.startsWith("$artifactIdWithoutVersion:")) {
                    continue
                }

                /*
                 * Normalize path
                 */

                val normalizedPath =
                    path.removePrefix("META-INF/libraries/")

                val jarPath =
                    "META-INF/libraries/$normalizedPath"

                val artifactEntry = jar.getJarEntry(jarPath)

                if (artifactEntry == null) {
                    System.err.println("Artifact jar not found in server:")
                    System.err.println(jarPath)
                    exitProcess(4)
                }

                /*
                 * Extract filename from path
                 */

                val fileName =
                    normalizedPath.substringAfterLast('/')

                val outputFile = File(outputDir, fileName)

                jar.getInputStream(artifactEntry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                /*
                 * Print full artifact id
                 */

                println(fullArtifactId)

                return
            }

            System.err.println("Artifact not found: $artifactIdWithoutVersion")
            exitProcess(5)
        }
    }
}