package com.compdog.authlibpatcher

import com.compdog.authlibpatcher.patchers.Patcher
import com.compdog.authlibpatcher.patchers.YggdrasilPatcher
import com.compdog.authlibpatcher.patchers.DiscoveryPatcher
import java.util.jar.JarFile

object AuthlibDetector {

    /**
     * Detect which version of authlib is in the jar and return the appropriate patcher
     * @return Patcher instance for the detected authlib version, or null if not recognized
     */
    fun detectPatcher(jar: JarFile): Patcher? {
        val entries = jar.entries()
        val entryNames = mutableSetOf<String>()

        while (entries.hasMoreElements()) {
            entryNames.add(entries.nextElement().name)
        }

        // Check for Yggdrasil version
        if (isYggdrasilVersion(entryNames)) {
            println("Detected Yggdrasil authlib version")
            return YggdrasilPatcher()
        }

        // Check for Discovery version
        if (isDiscoveryVersion(entryNames)) {
            println("Detected Discovery authlib version")
            return DiscoveryPatcher()
        }

        return null
    }

    private fun isYggdrasilVersion(entryNames: Set<String>): Boolean {
        return entryNames.contains("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class") &&
                entryNames.contains("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService.class")
    }

    private fun isDiscoveryVersion(entryNames: Set<String>): Boolean {
        return entryNames.contains("com/mojang/authlib/services/MinecraftServicesDiscoveryService.class") &&
                entryNames.contains("com/mojang/authlib/services/MinecraftServicesSessionService.class")
    }
}
