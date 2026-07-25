package com.nstut.economy.sound;

import com.nstut.Economy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegistries {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Economy.MOD_ID);

    public static final RegistryObject<SoundEvent> MONEY = SOUND_EVENTS.register("money",
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Economy.MOD_ID, "money")));
}
