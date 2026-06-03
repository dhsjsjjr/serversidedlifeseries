package net.mat0u5.lifeseries.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.Unpooled;
import net.mat0u5.lifeseries.LifeSeries;
import net.mat0u5.lifeseries.entity.fakeplayer.FakeClientConnection;
import net.mat0u5.lifeseries.network.NetworkHandlerServer;
import net.mat0u5.lifeseries.registries.MobRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <= 1.21.5
//import net.minecraft.network.PacketSendListener;
//? if >= 1.21.6
import io.netty.channel.ChannelFutureListener;

//? if <= 1.20.3 {
/*import net.mat0u5.lifeseries.network.NetworkHandlerServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
*///?}

//? if <= 1.20 {
/*import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1)
*///?} else {
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 1)
//?}
public class ServerCommonPacketListenerImplMixin {

    @Final
    @Shadow
    protected Connection connection;

    /**
     * For server-only operation: intercept outbound packets to vanilla (non-modded) clients.
     * - Blocks ClientboundAddEntityPacket for our custom entity types (Snail, TriviaBot,
     *   AngrySnowman) when the receiving client has not completed the LifeSeries handshake.
     *   Without this, vanilla clients crash with "Unknown entity type" / network protocol errors
     *   the moment a custom entity enters their view range.
     * - Still blocks all packets for FakeClientConnection as before.
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacket(Packet<?> packet, CallbackInfo ci) {
        if (LifeSeries.modFullyDisabled()) return;
        if (connection instanceof FakeClientConnection) {
            ci.cancel();
            return;
        }
        // Drop custom-entity spawn packets for vanilla clients to prevent protocol errors.
        if (packet instanceof ClientboundAddEntityPacket addEntity) {
            if (isModEntity(addEntity) && isVanillaClient()) {
                ci.cancel();
            }
        }
    }

    /** Returns true if this entity spawn packet is for one of our custom mob types. */
    private boolean isModEntity(ClientboundAddEntityPacket packet) {
        var type = packet.getType();
        return type == MobRegistry.SNAIL
            || type == MobRegistry.TRIVIA_BOT
            || type == MobRegistry.ANGRY_SNOWMAN;
    }

    /**
     * A client is considered "vanilla" (no LifeSeries mod) when it has not completed
     * the mod handshake. Since this mixin runs inside the packet listener itself,
     * we cast (Object)this to ServerGamePacketListenerImpl to get the player directly,
     * avoiding any need to access the protected connection field.
     */
    private boolean isVanillaClient() {
        try {
            if ((Object)this instanceof net.minecraft.server.network.ServerGamePacketListenerImpl handler) {
                return !NetworkHandlerServer.wasHandshakeSuccessful(handler.player);
            }
        } catch (Exception ignored) {}
        return false;
    }


    //? if <= 1.20 {
    /*@WrapOperation(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V")
    )
    public void send(Connection instance, Packet<?> packet, PacketSendListener packetSendListener, Operation<Void> original) {
        if (connection instanceof FakeClientConnection) return;
        original.call(instance, packet, packetSendListener);
    }
    *///?} else if <= 1.21.5 {
    /*@WrapOperation(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V")
    )
    public void send(Connection instance, Packet<?> packet, PacketSendListener callbacks, boolean flush, Operation<Void> original) {
        if (connection instanceof FakeClientConnection) return;
        original.call(instance, packet, callbacks, flush);
    }
    *///?} else {
    @WrapOperation(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    )
    public void send(Connection instance, Packet packet, ChannelFutureListener channelFutureListener, boolean b, Operation<Void> original) {
        if (connection instanceof FakeClientConnection) return;
        original.call(instance, packet, channelFutureListener, b);
    }
    //?}

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    public void disconnect(Component reason, CallbackInfo ci) {
        if (reason.getString().contains("lifeseries") && reason.getString().contains("registr")) {
            ci.cancel();
            return;
        }
        if (LifeSeries.modFullyDisabled()) return;
        if (connection instanceof FakeClientConnection) {
            ci.cancel();
        }
    }

    //? if <= 1.20 {
    /*@Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void onHandlePayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if ((Object)this instanceof ServerGamePacketListenerImpl gameListener) {
            Identifier id = packet.getIdentifier();
            if (NetworkHandlerServer.PAYLOAD_READERS.containsKey(id)) {
                CustomPacketPayload payload = NetworkHandlerServer.PAYLOAD_READERS.get(id).apply(packet.getData());
                NetworkHandlerServer.onCustomPayload(payload, gameListener.player);
            }
        }
    }
    *///?} else if <= 1.20.3 {
    /*@Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void onHandlePayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if ((Object)this instanceof ServerGamePacketListenerImpl gameListener) {
            NetworkHandlerServer.onCustomPayload(packet.payload(), gameListener.player);
        }
    }
    *///?}
}
