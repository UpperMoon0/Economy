package com.nstut.economy.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Initializes vanilla registries for JVM tests that use Items, Fluids, NBT, or
 * other Minecraft objects without launching a full game server.
 */
public abstract class MinecraftTestBase {
    static {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (Throwable error) {
            // Tolerate non-fatal early platform bootstrap assertions in plain JUnit JVM
        }
    }
}
