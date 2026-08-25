package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.command.EconomyCommands;
import com.nstut.economy.items.ItemRegistries;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.economy.sound.SoundRegistries;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Economy.MOD_ID)
@Mod.EventBusSubscriber(modid = Economy.MOD_ID)
public final class EconomyForge {
    public EconomyForge() {
        EventBuses.registerModEventBus(Economy.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        BlockRegistries.init();
        ItemRegistries.init();
        SoundRegistries.init();
        MarketNetwork.init();
        Economy.init();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }
}
