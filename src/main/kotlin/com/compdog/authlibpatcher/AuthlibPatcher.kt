@file:JvmName("AuthlibPatcher")

package com.compdog.authlibpatcher

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
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

    private lateinit var profiles: List<SpecialProfile>
    private lateinit var jarClassLoader: URLClassLoader

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

        profiles = loadProfiles(jsonFile)

        println("Loaded ${profiles.size} profiles")

        val outputJar = File(
            inputJar.parentFile,
            inputJar.nameWithoutExtension + "_patched.jar"
        )

        jarClassLoader = URLClassLoader(
            arrayOf(inputJar.toURI().toURL()),
            ClassLoader.getSystemClassLoader()
        )

        JarFile(inputJar).use { jar ->
            JarOutputStream(FileOutputStream(outputJar)).use { jos ->

                val entries = jar.entries()

                while (entries.hasMoreElements()) {

                    val entry = entries.nextElement()

                    val bytes = jar.getInputStream(entry).readBytes()

                    val newBytes = when (entry.name) {

                        "com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class" ->
                            patchAuthenticationService(bytes)

                        "com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService.class" ->
                            patchMinecraftSessionService(bytes)

                        else -> bytes
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

    private fun patchAuthenticationService(bytes: ByteArray): ByteArray {

        val cr = ClassReader(bytes)
        val cn = ClassNode()

        cr.accept(cn, 0)

        for (method in cn.methods) {

            if (method.name == "<init>") {

                val insns = method.instructions.toArray()

                for (insn in insns) {

                    if (insn is MethodInsnNode) {

                        if (
                            insn.opcode == Opcodes.INVOKEINTERFACE &&
                            insn.owner == "org/slf4j/Logger" &&
                            insn.name == "info"
                        ) {

                            val inject = InsnList()

                            inject.add(
                                FieldInsnNode(
                                    Opcodes.GETSTATIC,
                                    cn.name,
                                    "LOGGER",
                                    "Lorg/slf4j/Logger;"
                                )
                            )

                            inject.add(
                                LdcInsnNode(
                                    "============= PATCHED authlib by homvp =============\n"
                                )
                            )

                            inject.add(
                                InsnNode(Opcodes.ICONST_5)
                            )

                            inject.add(
                                MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    "java/lang/String",
                                    "repeat",
                                    "(I)Ljava/lang/String;",
                                    false
                                )
                            )

                            inject.add(
                                MethodInsnNode(
                                    Opcodes.INVOKEINTERFACE,
                                    "org/slf4j/Logger",
                                    "info",
                                    "(Ljava/lang/String;)V",
                                    true
                                )
                            )

                            method.instructions.insert(insn, inject)

                            println("Injected LOGGER.info patch")

                            return writeClass(cn)
                        }
                    }
                }
            }
        }

        return writeClass(cn)
    }

    private fun patchMinecraftSessionService(bytes: ByteArray): ByteArray {

        val cr = ClassReader(bytes)
        val cn = ClassNode()

        cr.accept(cn, 0)

        for (method in cn.methods) {

            if (method.name == "joinServer") {
                if(!patchJoinServer(method)) {
                    throw RuntimeException("Failed to patch joinServer")
                }
            }

            if (method.name == "hasJoinedServer") {
                if(!patchHasJoinedServer(cn, method))
                {
                    throw RuntimeException("Failed to patch hasJoinedServer")
                }
            }
        }

        return writeClass(cn)
    }

    private fun patchJoinServer(method: MethodNode): Boolean {

        val insns = method.instructions.toArray()

        for (insn in insns) {

            if (insn is MethodInsnNode) {

                if (insn.name == "toAuthenticationException") {

                    val prev = insn.previous
                    val next = insn.next

                    if (next is InsnNode && next.opcode == Opcodes.ATHROW) {

                        method.instructions.remove(prev)
                        method.instructions.remove(insn)
                        method.instructions.remove(next)

                        println("Removed throw e.toAuthenticationException();")

                        return true
                    }
                }
            }
        }

        return false
    }

    private fun patchHasJoinedServer(
        classNode: ClassNode,
        method: MethodNode
    ): Boolean {
        for (tcb in method.tryCatchBlocks) {

            var insn = tcb.start.next

            while (insn != tcb.end) {

                val next = insn.next

                if (insn.opcode == Opcodes.ACONST_NULL) {
                    var next = insn.next

                    if(next is LabelNode) {
                        next = next.next
                    }

                    if (next is InsnNode && next.opcode == Opcodes.ARETURN) {

                        val replacement = InsnList()

                        val endLabel = LabelNode()

                        for (profile in profiles) {

                            val nextProfile = LabelNode()

                            /*
                             * if(profileName.equals("username"))
                             */

                            replacement.add(
                                VarInsnNode(Opcodes.ALOAD, 1)
                            )

                            replacement.add(
                                LdcInsnNode(profile.username)
                            )

                            replacement.add(
                                MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    "java/lang/String",
                                    "equals",
                                    "(Ljava/lang/Object;)Z",
                                    false
                                )
                            )

                            replacement.add(
                                JumpInsnNode(
                                    Opcodes.IFEQ,
                                    nextProfile
                                )
                            )

                            /*
                             * LOGGER.info(...)
                             */

                            replacement.add(
                                FieldInsnNode(
                                    Opcodes.GETSTATIC,
                                    classNode.name,
                                    "LOGGER",
                                    "Lorg/slf4j/Logger;"
                                )
                            )

                            replacement.add(
                                LdcInsnNode(
                                    "${profile.username} is a special case, returning a profile without authentication"
                                )
                            )

                            replacement.add(
                                MethodInsnNode(
                                    Opcodes.INVOKEINTERFACE,
                                    "org/slf4j/Logger",
                                    "info",
                                    "(Ljava/lang/String;)V",
                                    true
                                )
                            )

                            /*
                             * return new ProfileResult(...)
                             */

                            replacement.add(
                                TypeInsnNode(
                                    Opcodes.NEW,
                                    "com/mojang/authlib/yggdrasil/ProfileResult"
                                )
                            )

                            replacement.add(
                                InsnNode(Opcodes.DUP)
                            )

                            replacement.add(
                                TypeInsnNode(
                                    Opcodes.NEW,
                                    "com/mojang/authlib/GameProfile"
                                )
                            )

                            replacement.add(
                                InsnNode(Opcodes.DUP)
                            )

                            replacement.add(
                                LdcInsnNode(profile.uuid)
                            )

                            replacement.add(
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "java/util/UUID",
                                    "fromString",
                                    "(Ljava/lang/String;)Ljava/util/UUID;",
                                    false
                                )
                            )

                            replacement.add(
                                VarInsnNode(Opcodes.ALOAD, 1)
                            )

                            replacement.add(
                                MethodInsnNode(
                                    Opcodes.INVOKESPECIAL,
                                    "com/mojang/authlib/GameProfile",
                                    "<init>",
                                    "(Ljava/util/UUID;Ljava/lang/String;)V",
                                    false
                                )
                            )

                            replacement.add(
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "java/util/Set",
                                    "of",
                                    "()Ljava/util/Set;",
                                    true
                                )
                            )

                            replacement.add(
                                MethodInsnNode(
                                    Opcodes.INVOKESPECIAL,
                                    "com/mojang/authlib/yggdrasil/ProfileResult",
                                    "<init>",
                                    "(Lcom/mojang/authlib/GameProfile;Ljava/util/Set;)V",
                                    false
                                )
                            )

                            replacement.add(
                                InsnNode(Opcodes.ARETURN)
                            )

                            replacement.add(nextProfile)
                        }

                        /*
                         * default return null;
                         */

                        replacement.add(
                            InsnNode(Opcodes.ACONST_NULL)
                        )

                        replacement.add(
                            InsnNode(Opcodes.ARETURN)
                        )

                        replacement.add(endLabel)

                        method.instructions.insertBefore(insn, replacement)

                        method.instructions.remove(insn)
                        method.instructions.remove(next)

                        println("Patched hasJoinedServer with ${profiles.size} profiles")

                        return true
                    }
                }

                insn = next
            }
        }

        return false
    }

    private fun writeClass(cn: ClassNode): ByteArray {

        cn.methods.forEach {
            it.instructions.resetLabels()
        }

        val cw = object : ClassWriter(
            COMPUTE_FRAMES or COMPUTE_MAXS
        ) {

            override fun getCommonSuperClass(
                type1: String,
                type2: String
            ): String {

                return try {

                    val c1 = Class.forName(
                        type1.replace('/', '.'),
                        false,
                        jarClassLoader
                    )

                    val c2 = Class.forName(
                        type2.replace('/', '.'),
                        false,
                        jarClassLoader
                    )

                    when {
                        c1.isAssignableFrom(c2) -> type1
                        c2.isAssignableFrom(c1) -> type2

                        c1.isInterface || c2.isInterface ->
                            "java/lang/Object"

                        else -> {

                            var current = c1

                            while (!current.isAssignableFrom(c2)) {
                                current = current.superclass
                            }

                            current.name.replace('.', '/')
                        }
                    }

                } catch (e: Exception) {

                    /*
                     * Fallback for missing deps
                     */

                    "java/lang/Object"
                }
            }
        }

        cn.accept(cw)

        return cw.toByteArray()
    }
}