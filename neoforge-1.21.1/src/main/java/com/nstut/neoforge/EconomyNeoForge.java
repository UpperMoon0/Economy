package com.nstut.neoforge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.command.EconomyCommands;
import com.nstut.economy.items.ItemRegistries;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.economy.server.EconomyServerLifecycle;
import com.nstut.economy.sound.SoundRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

@Mod(Economy.MOD_ID)
public final class EconomyNeoForge {

    public EconomyNeoForge(net.neoforged.bus.api.IEventBus modBus) {
        BlockRegistries.init();
        ItemRegistries.init();
        SoundRegistries.init();
        MarketNetwork.init();
        Economy.init();

        modBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                BlockRegistries.TANK_BE.get(),
                (tank, side) -> {
                    if (!com.nstut.economy.config.EconomyConfig.getInstance().isExternalAutomationAllowed()) {
                        return null;
                    }
                    return new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
                        @Override
                        public int getTanks() {
                            return 1;
                        }

                        @Override
                        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tankIndex) {
                            if (tankIndex != 0 || tank.getFluid().isEmpty()) {
                                return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                            }
                            return new net.neoforged.neoforge.fluids.FluidStack(tank.getFluid().getFluid(), tank.getFluidAmount());
                        }

                        @Override
                        public int getTankCapacity(int tankIndex) {
                            return tankIndex == 0 ? tank.getCapacity() : 0;
                        }

                        @Override
                        public boolean isFluidValid(int tankIndex, net.neoforged.neoforge.fluids.FluidStack stack) {
                            return tankIndex == 0 && !stack.isEmpty()
                                    && (tank.getFluid().isEmpty() || tank.getFluid().getFluid() == stack.getFluid());
                        }

                        @Override
                        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
                            if (resource.isEmpty()) return 0;
                            com.nstut.economy.trading.EconomyFluidStack probe = new com.nstut.economy.trading.EconomyFluidStack(resource.getFluid(), resource.getAmount());
                            return action.simulate() ? tank.simulateFill(probe) : tank.fill(probe);
                        }

                        @Override
                        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
                            if (action.simulate()) {
                                com.nstut.economy.trading.EconomyFluidStack have = tank.getFluid();
                                if (have.isEmpty() || have.getFluid() != resource.getFluid()) {
                                    return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                                }
                                int take = Math.min(have.getAmount(), resource.getAmount());
                                return take <= 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                                        : new net.neoforged.neoforge.fluids.FluidStack(have.getFluid(), take);
                            }
                            com.nstut.economy.trading.EconomyFluidStack drained = tank.drain(new com.nstut.economy.trading.EconomyFluidStack(resource.getFluid(), resource.getAmount()));
                            if (drained.isEmpty()) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                            return new net.neoforged.neoforge.fluids.FluidStack(drained.getFluid(), drained.getAmount());
                        }

                        @Override
                        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
                            com.nstut.economy.trading.EconomyFluidStack have = tank.getFluid();
                            if (have.isEmpty()) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                            return drain(new net.neoforged.neoforge.fluids.FluidStack(have.getFluid(),
                                    Math.min(maxDrain, have.getAmount())), action);
                        }
                    };
                }
        );
    }

    /**
     * Vaults and tanks hold escrowed market goods; breaking one must not let
     * another player walk away with its contents.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        BlockEntity be = level.getBlockEntity(event.getPos());
        UUID owner = null;
        String messageKey = null;
        if (be instanceof VaultBlockEntity vault) {
            owner = vault.getOwner();
            messageKey = "message.economy.vault.not_owner";
        } else if (be instanceof TankBlockEntity tank) {
            owner = tank.getOwner();
            messageKey = "message.economy.tank.not_owner";
        }
        if (owner == null || owner.equals(player.getUUID())) return;
        if (player.hasPermissions(2)) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer && messageKey != null) {
            serverPlayer.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        EconomyServerLifecycle.load(level);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        EconomyServerLifecycle.save();
        Economy.LOGGER.info("Economy data saved");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        EconomyServerLifecycle.tick(event.getServer());
    }
}
