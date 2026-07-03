@file:JvmName("AuthlibPatcher")

package com.compdog.authlibpatcher

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.system.exitProcess

data class SpecialProfile(
    val username: String,
    val uuid: String
)

object AuthlibPatcher {

    @JvmStatic
    fun main(args: Array<String>) {

        if (args.size < 2) {
            println("Usage: <input.jar> <profiles.json>")
            exitProcess(1)
        }

        val inputJar = File(args[0])
        val jsonFile = File(args[1])

        if (!inputJar.exists()) {
            System.err.println("Input jar does not exist: ${inputJar.absolutePath}")
            exitProcess(2)
        }

        if (!jsonFile.exists()) {
            System.err.println("JSON file does not exist: ${jsonFile.absolutePath}")
            exitProcess(3)
        }

        val profiles = loadProfiles(jsonFile)
        println("Loaded ${profiles.size} profiles")

        val outputJar = File(
            inputJar.parentFile,
            inputJar.nameWithoutExtension + "_patched.jar"
        )

        val jarClassLoader = URLClassLoader(
            arrayOf(inputJar.toURI().toURL()),
            ClassLoader.getSystemClassLoader()
        )

        JarFile(inputJar).use { jar ->
            // Detect which patcher to use based on authlib version
            val patcher = AuthlibDetector.detectPatcher(jar)

            if (patcher == null) {
                System.err.println("Could not detect authlib version in jar")
                exitProcess(4)
            }

            // Initialize the patcher with context
            patcher.initialize(jarClassLoader, profiles)

            JarOutputStream(FileOutputStream(outputJar)).use { jos ->

                val entries = jar.entries()

                while (entries.hasMoreElements()) {

                    val entry = entries.nextElement()
                    val bytes = jar.getInputStream(entry).readBytes()

                    // Let the patcher decide if and how to patch this entry
                    val newBytes = if (patcher.canPatchEntry(entry.name)) {
                        patcher.patch(bytes, entry.name) ?: bytes
                    } else {
                        bytes
                    }

                    val newEntry = JarEntry(entry.name)
                    jos.putNextEntry(newEntry)
                    jos.write(newBytes)
                    jos.closeEntry()
                }
            }
        }

        println("Patched jar written to: ${outputJar.absolutePath}")
    }

    private fun loadProfiles(file: File): List<SpecialProfile> {
        FileReader(file).use { reader ->

            val type = object : TypeToken<List<SpecialProfile>>() {}.type

            return Gson().fromJson(reader, type)
        }
    }
}