package com.nstut.economy.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import dev.architectury.platform.Platform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Channel-style networking facade mirroring the Architectury NetworkChannel API
 * on top of Architectury's native {@link NetworkManager}, which survives on
 * NeoForge 26.1 while the channel abstraction itself was removed.
 */
public final class NetworkChannel {
    private static final List<NetworkChannel> CHANNELS = new ArrayList<>();

    private final Identifier id;
    private final List<Registration<?>> c2sRegistrations = new ArrayList<>();
    private final List<Registration<?>> s2cRegistrations = new ArrayList<>();
    private final Map<Class<?>, CustomPacketPayload.Type<?>> types = new HashMap<>();

    private record Registration<T>(
            CustomPacketPayload.Type<Adapted<T>> type,
            StreamCodec<? super RegistryFriendlyByteBuf, Adapted<T>> codec,
            BiConsumer<T, Supplier<com.nstut.economy.network.NetworkManager.PacketContext>> handler) { }

    private record Adapted<T>(CustomPacketPayload.Type<Adapted<T>> type, T value) implements CustomPacketPayload { }

    private NetworkChannel(Identifier id) {
        this.id = id;
        CHANNELS.add(this);
    }

    public static NetworkChannel create(Identifier id) {
        return new NetworkChannel(id);
    }

    /** Registers a client-to-server packet; its handler runs on the server. */
    public <T> void registerC2S(Class<T> type,
                                BiConsumer<T, FriendlyByteBuf> encoder,
                                Function<FriendlyByteBuf, T> decoder,
                                BiConsumer<T, Supplier<com.nstut.economy.network.NetworkManager.PacketContext>> handler) {
        Registration<T> registration = createRegistration(type, encoder, decoder, handler);
        types.put(type, registration.type());
        c2sRegistrations.add(registration);
        NetworkManager.registerReceiver(NetworkManager.c2s(), registration.type(), registration.codec(),
                (payload, context) -> dispatch(registration, payload, context));
    }

    /**
     * Registers a server-to-client packet; its handler runs on the client once
     * {@link #registerClientReceivers()} runs during client setup.
     */
    public <T> void registerS2C(Class<T> type,
                                BiConsumer<T, FriendlyByteBuf> encoder,
                                Function<FriendlyByteBuf, T> decoder,
                                BiConsumer<T, Supplier<com.nstut.economy.network.NetworkManager.PacketContext>> handler) {
        Registration<T> registration = createRegistration(type, encoder, decoder, handler);
        types.put(type, registration.type());
        s2cRegistrations.add(registration);
        if (Platform.getEnvironment() != Env.CLIENT) {
            // Dedicated servers never run client setup; declare the payload so
            // the server side can serialize it when sending.
            NetworkManager.registerS2CPayloadType(registration.type(), registration.codec());
        }
    }

    /** Called from client setup to attach S2C handlers on the physical client. */
    public static void registerClientReceivers() {
        for (NetworkChannel channel : CHANNELS) {
            for (Registration<?> registration : channel.s2cRegistrations) {
                registerS2CReceiver(registration);
            }
        }
    }

    private static <T> void registerS2CReceiver(Registration<T> registration) {
        NetworkManager.registerReceiver(NetworkManager.s2c(), registration.type(), registration.codec(),
                (payload, context) -> dispatch(registration, payload, context));
    }

    private static <T> void dispatch(Registration<T> registration, Adapted<T> payload,
                                     NetworkManager.PacketContext context) {
        registration.handler().accept(payload.value(), () ->
                new com.nstut.economy.network.NetworkManager.PacketContext() {
                    @Override
                    public Player getPlayer() { return context.getPlayer(); }

                    @Override
                    public void queue(Runnable task) { context.queue(task); }
                });
    }

    private <T> Registration<T> createRegistration(Class<T> type,
                                                   BiConsumer<T, FriendlyByteBuf> encoder,
                                                   Function<FriendlyByteBuf, T> decoder,
                                                   BiConsumer<T, Supplier<com.nstut.economy.network.NetworkManager.PacketContext>> handler) {
        Identifier packetId = Identifier.fromNamespaceAndPath(id.getNamespace(),
                id.getPath() + "/" + type.getSimpleName().toLowerCase(Locale.ROOT));
        CustomPacketPayload.Type<Adapted<T>> payloadType = new CustomPacketPayload.Type<>(packetId);
        StreamCodec<RegistryFriendlyByteBuf, Adapted<T>> codec = CustomPacketPayload.codec(
                (Adapted<T> value, RegistryFriendlyByteBuf buf) -> encoder.accept(value.value(), buf),
                buf -> new Adapted<>(payloadType, decoder.apply(buf)));
        return new Registration<>(payloadType, codec, handler);
    }

    public void sendToServer(Object payload) {
        NetworkManager.sendToServer(adapt(payload));
    }

    public void sendToPlayer(ServerPlayer player, Object payload) {
        NetworkManager.sendToPlayer(player, adapt(payload));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CustomPacketPayload adapt(Object payload) {
        CustomPacketPayload.Type type = types.get(payload.getClass());
        if (type == null) {
            throw new IllegalArgumentException("Payload type not registered: " + payload.getClass().getName());
        }
        return new Adapted(type, payload);
    }
}
