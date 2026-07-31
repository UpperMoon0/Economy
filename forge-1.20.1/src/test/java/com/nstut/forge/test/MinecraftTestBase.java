package com.nstut.forge.test;

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
        } catch (ExceptionInInitializerError error) {
            // Forge 47's legacy event-bus network bootstrap expects a no-arg
            // NetworkEvent constructor that is unavailable in a plain JUnit JVM.
            // Vanilla registries and item/fluid bootstrap complete before that
            // final network hook, which is sufficient for these non-network-runtime
            // object tests.
            if (!causedByForgeNetworkBootstrap(error)) {
                throw error;
            }
        }
    }

    private static boolean causedByForgeNetworkBootstrap(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            for (StackTraceElement frame : current.getStackTrace()) {
                if ("net.minecraftforge.network.NetworkHooks".equals(frame.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
