package com.nstut.economy.sound;

import com.nstut.Economy;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class SoundRegistries {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Economy.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> MONEY = SOUND_EVENTS.register("money",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Economy.MOD_ID, "money")));

    public static void init() {
        SOUND_EVENTS.register();
    }
}
