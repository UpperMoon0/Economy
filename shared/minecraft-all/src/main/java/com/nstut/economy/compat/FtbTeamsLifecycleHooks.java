package com.nstut.economy.compat;

import com.nstut.Economy;
import com.nstut.economy.api.TeamRef;
import com.nstut.economy.server.TeamWalletLifecycle;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/** Optional adapters verified against the pinned FTB source tags; no mandatory FTB linkage. */
public final class FtbTeamsLifecycleHooks {
    private static boolean installed;
    private FtbTeamsLifecycleHooks() {}

    public static synchronized void install() {
        if (installed) return;
        try {
            Class<?> teamType = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            try {
                Class<?> events = Class.forName("dev.ftb.mods.ftbteams.api.event.TeamEvent");
                Class<?> eventApi = Class.forName("dev.architectury.event.Event");
                Method register = eventApi.getMethod("register", Object.class);
                Method getTeam = events.getMethod("getTeam");
                for (String name : new String[]{"CREATED", "LOADED", "OWNERSHIP_TRANSFERRED", "DELETED"}) {
                    boolean deleted = name.equals("DELETED");
                    Consumer<Object> listener = event -> handle(event, getTeam, teamType, deleted, false);
                    register.invoke(events.getField(name).get(null), listener);
                }
            } catch (ClassNotFoundException nativeEvents) {
                Class<?> neoForge = Class.forName("net.neoforged.neoforge.common.NeoForge");
                Object bus = neoForge.getField("EVENT_BUS").get(null);
                Class<?> busApi = Class.forName("net.neoforged.bus.api.IEventBus");
                Method addListener = busApi.getMethod("addListener", Class.class, Consumer.class);
                for (String name : new String[]{"TeamCreated", "TeamLoaded", "PlayerTransferredOwnership", "TeamDeleted"}) {
                    Class<?> event = Class.forName("dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent$" + name);
                    Method getData = event.getMethod("getEventData");
                    boolean deleted = name.equals("TeamDeleted");
                    Consumer<Object> listener = value -> handle(value, getData, teamType, deleted, true);
                    addListener.invoke(bus, event, listener);
                }
            }
            installed = true;
        } catch (ClassNotFoundException absent) {
            // Economy remains usable without FTB Teams.
        } catch (ReflectiveOperationException | LinkageError incompatible) {
            Economy.LOGGER.error("FTB Teams lifecycle adapter could not register; authoritative polling remains enabled", incompatible);
        }
    }
    private static void handle(Object event, Method unwrap, Class<?> teamType, boolean deleted, boolean nativeEvent) {
        try {
            Object value = unwrap.invoke(event);
            Object team = nativeEvent ? value.getClass().getMethod("team").invoke(value) : value;
            if (!(Boolean) teamType.getMethod("isPartyTeam").invoke(team)) return;
            TeamRef ref = new TeamRef((UUID) teamType.getMethod("getId").invoke(team),
                    String.valueOf(teamType.getMethod("getShortName").invoke(team)),
                    (UUID) teamType.getMethod("getOwner").invoke(team));
            if (deleted) TeamWalletLifecycle.deleted(ref); else TeamWalletLifecycle.observe(ref);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Economy.LOGGER.error("Could not capture FTB Teams lifecycle event", failure);
        }
    }
}
