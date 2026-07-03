package com.compdog.authlibpatcher.patchers

import com.compdog.authlibpatcher.SpecialProfile
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.net.URLClassLoader

class DiscoveryPatcher : Patcher {

    private lateinit var profiles: List<SpecialProfile>
    private lateinit var jarClassLoader: URLClassLoader

    override fun initialize(jarClassLoader: URLClassLoader, profiles: List<SpecialProfile>) {
        this.jarClassLoader = jarClassLoader
        this.profiles = profiles
    }

    override fun canPatchEntry(entryName: String): Boolean {
        return entryName == "com/mojang/authlib/services/MinecraftServicesDiscoveryService.class" ||
                entryName == "com/mojang/authlib/services/MinecraftServicesSessionService.class"
    }

    override fun patch(bytes: ByteArray, entryName: String): ByteArray? {
        return when (entryName) {
            "com/mojang/authlib/services/MinecraftServicesDiscoveryService.class" ->
                patchDiscoveryService(bytes)

            "com/mojang/authlib/services/MinecraftServicesSessionService.class" ->
                patchMinecraftSessionService(bytes)

            else -> null
        }
    }

    private fun patchDiscoveryService(bytes: ByteArray): ByteArray {

        val cr = ClassReader(bytes)
        val cn = ClassNode()

        cr.accept(cn, 0)

        for (method in cn.methods) {

            if (method.name == "<init>") {

                val insns = method.instructions.toArray()

                for (insn in insns) {

                    if (
                        insn is MethodInsnNode &&
                        insn.opcode == Opcodes.INVOKESPECIAL &&
                        insn.name == "<init>" &&
                        insn.owner != cn.name
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

        return writeClass(cn)
    }

    private fun patchMinecraftSessionService(bytes: ByteArray): ByteArray {

        val cr = ClassReader(bytes)
        val cn = ClassNode()

        cr.accept(cn, 0)

        for (method in cn.methods) {

            if (method.name == "joinServer") {
                if (!patchJoinServer(method)) {
                    throw RuntimeException("Failed to patch joinServer")
                }
            }

            if (method.name == "hasJoinedServer") {
                if (!patchHasJoinedServer(cn, method)) {
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

                    if (next is LabelNode) {
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
                                    "com/mojang/authlib/services/ProfileResult"
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
                                    "com/mojang/authlib/services/ProfileResult",
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
            ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS
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
