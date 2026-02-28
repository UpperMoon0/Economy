package com.nstut.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.nstut.Economy;

@Mod(Economy.MOD_ID)
public final class EconomyForge {
    public EconomyForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Economy.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Economy.init();
    }
}
