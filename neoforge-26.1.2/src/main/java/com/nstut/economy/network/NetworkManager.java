package com.nstut.economy.network;

import net.minecraft.world.entity.player.Player;

/**
 * Minimal replacement for Architectury's NetworkManager, providing the packet
 * context shape used by Economy's packet handlers.
 */
public final class NetworkManager {
    private NetworkManager() { }

    public interface PacketContext {
        Player getPlayer();

        void queue(Runnable task);
    }
}
