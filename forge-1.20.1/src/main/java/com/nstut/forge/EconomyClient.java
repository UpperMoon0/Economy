package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.blocks.VaultMenu;
import com.nstut.economy.client.MarketScreen;
import com.nstut.economy.client.TankRenderer;
import com.nstut.economy.client.TankScreen;
import com.nstut.economy.client.VaultScreen;
import com.nstut.economy.testing.LiveJoinClientProbe;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = Economy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EconomyClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerScreen(BlockRegistries.MARKET_MENU.get(), args ->
                    new MarketScreen((MarketMenu) args[0], (Inventory) args[1], (Component) args[2]));
            registerScreen(BlockRegistries.VAULT_MENU.get(), args ->
                    new VaultScreen((VaultMenu) args[0], (Inventory) args[1], (Component) args[2]));
            registerScreen(BlockRegistries.TANK_MENU.get(), args ->
                    new TankScreen((TankMenu) args[0], (Inventory) args[1], (Component) args[2]));
            LiveJoinClientProbe.register();
        });
    }

    private static void registerScreen(MenuType<?> type, Function<Object[], Object> factory) {
        try {
            Object screenConstructor = Proxy.newProxyInstance(
                    EconomyClient.class.getClassLoader(),
                    new Class<?>[]{MenuScreens.ScreenConstructor.class},
                    (proxy, method, args) -> {
                        if ("create".equals(method.getName())) {
                            return factory.apply(args);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(method.getName())) {
                            return "ScreenConstructorProxy@" + Integer.toHexString(System.identityHashCode(proxy));
                        }
                        return null;
                    }
            );
            Method m = MenuScreens.class.getMethod("register", MenuType.class, MenuScreens.ScreenConstructor.class);
            m.invoke(null, type, screenConstructor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register menu screen for " + type, e);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockRegistries.TANK_BE.get(), TankRenderer::new);
    }
}
