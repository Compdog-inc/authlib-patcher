@file:JvmName("ServerPatcher")

package com.compdog.authlibpatcher

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

object ServerPatcher {

    @JvmStatic
    fun main(args: Array<String>) {

        if (args.size < 3) {
            println("Usage: <server.jar> <group:artifact:version> <artifact.jar>")
            return
        }

        val serverJar = File(args[0])
        val targetArtifact = args[1]
        val artifactJar = File(args[2])

        if (!serverJar.exists() || !artifactJar.exists()) {
            println("Input file missing")
            return
        }

        val outputJar = File(
            serverJar.parentFile,
            serverJar.nameWithoutExtension + "_patched.jar"
        )

        val newHash = sha256Hex(artifactJar)

        println("Computed SHA-256: $newHash")

        JarFile(serverJar).use { jar ->
            JarOutputStream(FileOutputStream(outputJar)).use { jos ->

                val entries = jar.entries()

                while (entries.hasMoreElements()) {

                    val entry = entries.nextElement()
                    val bytes = jar.getInputStream(entry).readBytes()

                    if (entry.name == "META-INF/libraries.list") {

                        val updated = patchLibrariesList(
                            String(bytes),
                            targetArtifact,
                            newHash,
                            artifactJar
                        )

                        jos.putNextEntry(JarEntry(entry.name))
                        jos.write(updated.toByteArray())
                        jos.closeEntry()

                    } else {

                        jos.putNextEntry(JarEntry(entry.name))
                        jos.write(bytes)
                        jos.closeEntry()
                    }
                }

                // inject artifact jar into META-INF/libraries/
                val targetPath = buildLibraryPath(targetArtifact, artifactJar)

                jos.putNextEntry(JarEntry("META-INF/libraries/$targetPath"))
                jos.write(artifactJar.readBytes())
                jos.closeEntry()
            }
        }

        println("Patched server jar written: ${outputJar.absolutePath}")
    }

    private fun patchLibrariesList(
        content: String,
        artifactId: String,
        newHash: String,
        artifactJar: File
    ): String {

        val lines = content.lines().toMutableList()

        for (i in lines.indices) {

            val line = lines[i]
            if (!line.contains(artifactId)) continue

            val parts = line.split("\t")

            if (parts.size < 3) continue

            val oldHash = parts[0]
            val id = parts[1]
            val path = parts[2]

            if (id != artifactId) continue

            val newPath = buildLibraryPath(artifactId, artifactJar)

            val updated =
                "$newHash\t$artifactId\t$newPath"

            println("Replacing:")
            println("OLD: $line")
            println("NEW: $updated")

            lines[i] = updated
        }

        return lines.joinToString("\n")
    }

    private fun buildLibraryPath(
        artifactId: String,
        artifactJar: File
    ): String {

        val parts = artifactId.split(":")

        val groupPath = parts[0].replace(".", "/")
        val artifact = parts[1]
        val version = parts[2]

        val fileName = artifactJar.name

        return "$groupPath/$artifact/$version/$fileName"
    }

    private fun sha256Hex(file: File): String {

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)

        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }
}