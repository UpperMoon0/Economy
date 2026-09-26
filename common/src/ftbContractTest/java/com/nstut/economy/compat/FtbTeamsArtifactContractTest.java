package com.nstut.economy.compat;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Reads published class files directly: no local FTB doubles or Minecraft class loading. */
class FtbTeamsArtifactContractTest {
    private static final String API = "dev/ftb/mods/ftbteams/api/";

    @TestFactory
    Stream<DynamicTest> supportedArtifactsExposeTheReflectionContract() {
        String[] artifacts = System.getProperty("economy.ftbArtifacts").split(File.pathSeparator);
        assertEquals(5, artifacts.length, "Every production loader target needs an actual artifact");
        return Arrays.stream(artifacts).map(Path::of).map(path ->
                DynamicTest.dynamicTest(path.getFileName().toString(), () -> verify(path)));
    }

    private static void verify(Path path) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            // All methods resolved by FtbTeamsTeamEconomyProvider.createIfPresent(),
            // including return descriptors, public visibility, and api() being static.
            method(jar, "FTBTeamsAPI", "api", "()L" + API + "FTBTeamsAPI$API;", true);
            method(jar, "FTBTeamsAPI$API", "isManagerLoaded", "()Z", false);
            method(jar, "FTBTeamsAPI$API", "getManager", "()L" + API + "TeamManager;", false);
            for (String name : List.of("getTeamForPlayerID", "getTeamByID")) {
                method(jar, "TeamManager", name, "(Ljava/util/UUID;)Ljava/util/Optional;", false);
            }
            method(jar, "Team", "isPartyTeam", "()Z", false);
            method(jar, "Team", "getId", "()Ljava/util/UUID;", false);
            method(jar, "Team", "getOwner", "()Ljava/util/UUID;", false);
            method(jar, "Team", "getShortName", "()Ljava/lang/String;", false);
            method(jar, "Team", "getMembers", "()Ljava/util/Set;", false);
            method(jar, "Team", "getRankForPlayer", "(Ljava/util/UUID;)L" + API + "TeamRank;", false);
            ClassNode rank = read(jar, "TeamRank");
            assertEquals("java/lang/Enum", rank.superName);
            for (String name : List.of("MEMBER", "OFFICER", "OWNER")) {
                assertTrue(rank.fields.stream().anyMatch(field -> field.name.equals(name)
                        && field.desc.equals("L" + API + "TeamRank;")
                        && (field.access & Opcodes.ACC_ENUM) != 0), "Missing rank " + name);
            }
        }
    }

    private static void method(JarFile jar, String type, String name, String descriptor, boolean isStatic) throws Exception {
        ClassNode node = read(jar, type);
        assertTrue((node.access & Opcodes.ACC_PUBLIC) != 0, type + " must be public");
        assertTrue(node.methods.stream().anyMatch(method -> method.name.equals(name)
                        && method.desc.equals(descriptor)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
                        && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic),
                type + "." + name + descriptor + " is missing or incompatible in " + jar.getName());
    }

    private static ClassNode read(JarFile jar, String type) throws Exception {
        var entry = jar.getJarEntry(API + type + ".class");
        assertNotNull(entry, "Missing published API class " + type);
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
