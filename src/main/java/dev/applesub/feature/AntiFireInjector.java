package dev.applesub.feature;

import dev.applesub.AppleSubPlugin;
import dev.applesub.data.Feature;
import dev.applesub.data.PlayerData;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Hides the fire overlay: the client draws it from the "on fire" bit of the player's own
 * shared entity flags, so that bit is cleared in metadata packets sent about the player
 * to that same player. Other players still see them burning and fire damage is unchanged.
 *
 * <p>Paper 1.21.11 runs with Mojang mappings, so the server classes are resolved by their
 * Mojang names via reflection and no NMS compile dependency is needed.
 */
public final class AntiFireInjector {

    private static final String HANDLER_NAME = "applesub_anti_fire";
    private static final String PACKET_HANDLER = "packet_handler";
    private static final int SHARED_FLAGS_ID = 0;
    private static final byte ON_FIRE_BIT = 0x01;

    private final AppleSubPlugin plugin;
    private final Class<?> setEntityDataPacket;
    private final MethodHandle packetId;
    private final MethodHandle packetItems;
    private final MethodHandle packetConstructor;
    private final MethodHandle valueId;
    private final MethodHandle valueSerializer;
    private final MethodHandle valueValue;
    private final MethodHandle valueConstructor;
    private final Method getHandle;
    private final Field playerConnection;
    private final Field networkConnection;
    private final Field channel;
    private final AtomicBoolean errorLogged = new AtomicBoolean();

    private AntiFireInjector(AppleSubPlugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        ClassLoader loader = plugin.getServer().getClass().getClassLoader();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        setEntityDataPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket", false, loader);
        packetId = lookup.unreflect(setEntityDataPacket.getMethod("id"));
        packetItems = lookup.unreflect(setEntityDataPacket.getMethod("packedItems"));
        packetConstructor = lookup.unreflectConstructor(setEntityDataPacket.getConstructor(int.class, List.class));

        Class<?> dataValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue", false, loader);
        valueId = lookup.unreflect(dataValue.getMethod("id"));
        valueSerializer = lookup.unreflect(dataValue.getMethod("serializer"));
        valueValue = lookup.unreflect(dataValue.getMethod("value"));
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : dataValue.getConstructors()) {
            if (candidate.getParameterCount() == 3 && candidate.getParameterTypes()[0] == int.class) {
                constructor = candidate;
            }
        }
        if (constructor == null) {
            throw new NoSuchMethodException("SynchedEntityData.DataValue(int, EntityDataSerializer, Object)");
        }
        valueConstructor = lookup.unreflectConstructor(constructor);

        getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer", false, loader).getMethod("getHandle");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer", false, loader);
        Class<?> gameListener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl", false, loader);
        Class<?> connection = Class.forName("net.minecraft.network.Connection", false, loader);
        playerConnection = findField(serverPlayer, gameListener);
        networkConnection = findField(gameListener, connection);
        channel = findField(connection, Channel.class);
    }

    /** @return the injector, or null if this server build is not compatible (anti-fire then does nothing). */
    public static AntiFireInjector create(AppleSubPlugin plugin) {
        try {
            return new AntiFireInjector(plugin);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "Анти-огонь недоступен на этой версии сервера", e);
            return null;
        }
    }

    private static Field findField(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(type.getName() + " in " + owner.getName());
    }

    private Channel channelOf(Player player) throws ReflectiveOperationException {
        Object handle = getHandle.invoke(player);
        Object listener = playerConnection.get(handle);
        if (listener == null) {
            return null;
        }
        Object connection = networkConnection.get(listener);
        return connection == null ? null : (Channel) channel.get(connection);
    }

    public void inject(Player player) {
        try {
            Channel ch = channelOf(player);
            if (ch == null || !ch.isOpen()) {
                return;
            }
            ChannelPipeline pipeline = ch.pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
            if (pipeline.get(PACKET_HANDLER) != null) {
                pipeline.addBefore(PACKET_HANDLER, HANDLER_NAME, new Handler(player));
            }
        } catch (Exception e) {
            logOnce(e);
        }
    }

    public void eject(Player player) {
        try {
            Channel ch = channelOf(player);
            if (ch != null && ch.pipeline().get(HANDLER_NAME) != null) {
                ch.pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {
            // Channel already closed.
        }
    }

    private Object filter(Player player, Object packet) {
        try {
            int entityId = (int) packetId.invoke(packet);
            if (entityId != player.getEntityId()) {
                return packet;
            }
            PlayerData data = plugin.dataManager().get(player.getUniqueId());
            if (data == null || !data.isActive(Feature.ANTI_FIRE)) {
                return packet;
            }
            List<?> items = (List<?>) packetItems.invoke(packet);
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if ((int) valueId.invoke(item) != SHARED_FLAGS_ID) {
                    continue;
                }
                if (valueValue.invoke(item) instanceof Byte flags && (flags & ON_FIRE_BIT) != 0) {
                    List<Object> copy = new ArrayList<>(items);
                    copy.set(i, valueConstructor.invoke(SHARED_FLAGS_ID, valueSerializer.invoke(item), (byte) (flags & ~ON_FIRE_BIT)));
                    return packetConstructor.invoke(entityId, copy);
                }
                return packet;
            }
            return packet;
        } catch (Throwable t) {
            logOnce(t);
            return packet;
        }
    }

    private void logOnce(Throwable t) {
        if (errorLogged.compareAndSet(false, true)) {
            plugin.getLogger().log(Level.WARNING, "Ошибка анти-огня (дальнейшие ошибки скрыты)", t);
        }
    }

    private final class Handler extends ChannelOutboundHandlerAdapter {

        private final Player player;
        private final UUID uuid;

        private Handler(Player player) {
            this.player = player;
            this.uuid = player.getUniqueId();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (setEntityDataPacket.isInstance(msg)) {
                msg = filter(player, msg);
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public String toString() {
            return "AppleSubAntiFire[" + uuid + "]";
        }
    }
}
