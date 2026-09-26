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
    private static final String EVENTS = API + "event/";
    private static final String NEOFORGE_EVENTS = API + "neoforge/FTBTeamsEvent$";
    private static final String LIB_NEOFORGE = "dev/ftb/mods/ftblibrary/api/neoforge/BaseEventWithData";

    @TestFactory
    Stream<DynamicTest> supportedArtifactsExposeTheReflectionContract() {
        String[] artifacts = System.getProperty("economy.ftbArtifacts").split(File.pathSeparator);
        String[] libraries = System.getProperty("economy.ftbLibraryArtifacts").split(File.pathSeparator);
        assertEquals(5, artifacts.length, "Every production loader target needs an actual FTB Teams artifact");
        assertEquals(5, libraries.length, "Every production loader target needs an actual FTB Library artifact");
        List<Path> libraryPaths = Arrays.stream(libraries).map(Path::of).toList();
        return Arrays.stream(artifacts).map(Path::of).map(path ->
                DynamicTest.dynamicTest(path.getFileName().toString(), () -> verify(path, libraryPaths)));
    }

    private static void verify(Path path, List<Path> libraryPaths) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            // All methods resolved by FtbTeamsTeamEconomyProvider.createIfPresent(),
            // including return descriptors, public visibility, and api() being static.
            method(jar, API + "FTBTeamsAPI", "api", "()L" + API + "FTBTeamsAPI$API;", true);
            method(jar, API + "FTBTeamsAPI$API", "isManagerLoaded", "()Z", false);
            method(jar, API + "FTBTeamsAPI$API", "getManager", "()L" + API + "TeamManager;", false);
            for (String name : List.of("getTeamForPlayerID", "getTeamByID")) {
                method(jar, API + "TeamManager", name, "(Ljava/util/UUID;)Ljava/util/Optional;", false);
            }
            method(jar, API + "Team", "isPartyTeam", "()Z", false);
            method(jar, API + "Team", "getId", "()Ljava/util/UUID;", false);
            method(jar, API + "Team", "getOwner", "()Ljava/util/UUID;", false);
            method(jar, API + "Team", "getShortName", "()Ljava/lang/String;", false);
            method(jar, API + "Team", "getMembers", "()Ljava/util/Set;", false);
            method(jar, API + "Team", "getRankForPlayer", "(Ljava/util/UUID;)L" + API + "TeamRank;", false);
            ClassNode rank = read(jar, API + "TeamRank");
            assertEquals("java/lang/Enum", rank.superName);
            for (String name : List.of("MEMBER", "OFFICER", "OWNER")) {
                assertTrue(rank.fields.stream().anyMatch(field -> field.name.equals(name)
                        && field.desc.equals("L" + API + "TeamRank;")
                        && (field.access & Opcodes.ACC_ENUM) != 0), "Missing rank " + name);
            }

            if (jar.getJarEntry(EVENTS + "TeamEvent.class") != null) {
                verifyArchitecturyLifecycle(jar);
            } else {
                verifyNeoForgeLifecycle(jar, libraryPaths);
            }
        }
    }

    private static void verifyArchitecturyLifecycle(JarFile jar) throws Exception {
        ClassNode event = read(jar, EVENTS + "TeamEvent");
        assertTrue((event.access & Opcodes.ACC_PUBLIC) != 0, "TeamEvent must be public");
        for (String name : List.of("CREATED", "LOADED", "OWNERSHIP_TRANSFERRED", "DELETED")) {
            assertTrue(event.fields.stream().anyMatch(field -> field.name.equals(name)
                    && field.desc.equals("Ldev/architectury/event/Event;")
                    && (field.access & Opcodes.ACC_PUBLIC) != 0
                    && (field.access & Opcodes.ACC_STATIC) != 0), "Missing lifecycle event " + name);
        }
        method(jar, EVENTS + "TeamEvent", "getTeam", "()L" + API + "Team;", false);
    }

    private static void verifyNeoForgeLifecycle(JarFile jar, List<Path> libraryPaths) throws Exception {
        for (String wrapper : List.of("TeamCreated", "TeamLoaded", "PlayerTransferredOwnership", "TeamDeleted")) {
            ClassNode event = read(jar, NEOFORGE_EVENTS + wrapper);
            assertTrue((event.access & Opcodes.ACC_PUBLIC) != 0, wrapper + " event must be public");
            assertEquals(LIB_NEOFORGE, event.superName, wrapper + " must expose BaseEventWithData#getEventData");
        }
        for (String data : List.of("TeamCreatedEvent$Data", "TeamLoadedEvent$Data",
                "PlayerTransferredOwnershipEvent$Data", "TeamDeletedEvent$Data")) {
            method(jar, EVENTS + data, "team", "()L" + API + "Team;", false);
        }

        Path library = libraryPaths.stream().filter(FtbTeamsArtifactContractTest::containsBaseEventWithData)
                .findFirst().orElseThrow(() -> new AssertionError("Missing FTB Library NeoForge artifact with BaseEventWithData"));
        try (JarFile libraryJar = new JarFile(library.toFile())) {
            method(libraryJar, LIB_NEOFORGE, "getEventData", "()Ljava/lang/Object;", false);
        }
    }

    private static boolean containsBaseEventWithData(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getJarEntry(LIB_NEOFORGE + ".class") != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void method(JarFile jar, String internalType, String name, String descriptor, boolean isStatic) throws Exception {
        ClassNode node = read(jar, internalType);
        assertTrue((node.access & Opcodes.ACC_PUBLIC) != 0, internalType + " must be public");
        assertTrue(node.methods.stream().anyMatch(method -> method.name.equals(name)
                        && method.desc.equals(descriptor)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
                        && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic),
                internalType + "." + name + descriptor + " is missing or incompatible in " + jar.getName());
    }

    private static ClassNode read(JarFile jar, String internalType) throws Exception {
        var entry = jar.getJarEntry(internalType + ".class");
        assertNotNull(entry, "Missing published API class " + internalType);
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
