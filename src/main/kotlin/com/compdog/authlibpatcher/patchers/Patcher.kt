package com.compdog.authlibpatcher.patchers

import com.compdog.authlibpatcher.SpecialProfile
import java.net.URLClassLoader

interface Patcher {
    /**
     * Initialize the patcher with required context
     */
    fun initialize(jarClassLoader: URLClassLoader, profiles: List<SpecialProfile>)

    /**
     * Check if this patcher can handle the given jar entry
     */
    fun canPatchEntry(entryName: String): Boolean

    /**
     * Patch the class bytes and return the modified bytes, or null if no patching needed
     */
    fun patch(bytes: ByteArray, entryName: String): ByteArray?
}
