package com.example.addon.mixin;

import com.example.addon.tactical.ServerDetector;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版资源包处理入口不经过 Meteor 收包事件时，在此处统一接管。 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackPushMixin {
    @Shadow public abstract void send(Packet<?> packet);

    @Inject(
        method = "handleResourcePackPush(Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void yiyiaddon$handleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        ServerDetector module = Modules.get().get(ServerDetector.class);
        if (module != null && module.handleResourcePackPushFromVanilla(packet, this::send)) ci.cancel();
    }
}
