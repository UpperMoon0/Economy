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
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkChannel {
    private static final List<NetworkChannel> CHANNELS = new ArrayList<>();

    private final ResourceLocation id;
    private final List<Registration<?>> c2sRegistrations = new ArrayList<>();
    private final List<Registration<?>> s2cRegistrations = new ArrayList<>();
    private final Map<Class<?>, CustomPacketPayload.Type<?>> types = new HashMap<>();

    private record Registration<T>(
            CustomPacketPayload.Type<Adapted<T>> type,
            StreamCodec<? super RegistryFriendlyByteBuf, Adapted<T>> codec,
            BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler) { }

    private record Adapted<T>(CustomPacketPayload.Type<Adapted<T>> type, T value) implements CustomPacketPayload { }

    private NetworkChannel(ResourceLocation id) {
        this.id = id;
        CHANNELS.add(this);
    }

    public static NetworkChannel create(ResourceLocation id) {
        return new NetworkChannel(id);
    }

    public <T> void registerC2S(Class<T> type,
                                BiConsumer<T, FriendlyByteBuf> encoder,
                                Function<FriendlyByteBuf, T> decoder,
                                BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler) {
        Registration<T> registration = createRegistration(type, encoder, decoder, handler);
        types.put(type, registration.type());
        c2sRegistrations.add(registration);
        NetworkManager.registerReceiver(NetworkManager.c2s(), registration.type(), registration.codec(),
                (payload, context) -> dispatch(registration, payload, context));
    }

    public <T> void registerS2C(Class<T> type,
                                BiConsumer<T, FriendlyByteBuf> encoder,
                                Function<FriendlyByteBuf, T> decoder,
                                BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler) {
        Registration<T> registration = createRegistration(type, encoder, decoder, handler);
        types.put(type, registration.type());
        s2cRegistrations.add(registration);
        if (Platform.getEnv() != EnvType.CLIENT) {
            NetworkManager.registerS2CPayloadType(registration.type(), registration.codec());
        }
    }

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
        registration.handler().accept(payload.value(), () -> context);
    }

    private <T> Registration<T> createRegistration(Class<T> type,
                                                   BiConsumer<T, FriendlyByteBuf> encoder,
                                                   Function<FriendlyByteBuf, T> decoder,
                                                   BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler) {
        ResourceLocation packetId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                id.getPath() + "/" + type.getSimpleName().toLowerCase(Locale.ROOT));
        CustomPacketPayload.Type<Adapted<T>> payloadType = new CustomPacketPayload.Type<>(packetId);
        StreamCodec<RegistryFriendlyByteBuf, Adapted<T>> codec = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, Adapted<T> value) -> encoder.accept(value.value(), buf),
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
