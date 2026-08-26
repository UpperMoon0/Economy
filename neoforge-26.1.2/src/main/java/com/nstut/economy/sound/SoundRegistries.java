package com.nstut.economy.sound;

import com.nstut.Economy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SoundRegistries {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Economy.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MONEY = SOUND_EVENTS.register("money",
        () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Economy.MOD_ID, "money")));

    public static void init(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
