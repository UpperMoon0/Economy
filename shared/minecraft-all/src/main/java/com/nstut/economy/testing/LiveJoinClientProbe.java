package com.nstut.economy.testing;

import com.nstut.Economy;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

/** Reports a successful real client/server join once a playable client world exists. */
public final class LiveJoinClientProbe {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private LiveJoinClientProbe() {
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ClientTickEvent.CLIENT_POST.register(LiveJoinClientProbe::onClientTick);
        }
    }

    private static void onClientTick(Minecraft client) {
        if (LiveJoinTestProtocol.isEnabled()
                && client.player != null
                && client.level != null
                && client.getConnection() != null
                && LiveJoinTestProtocol.markReported()) {
            Economy.LOGGER.info(LiveJoinTestProtocol.PASS_MARKER);
            LiveJoinTestProtocol.stopClient(client::stop);
        }
    }
}
