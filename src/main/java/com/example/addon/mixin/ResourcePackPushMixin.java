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

/**
 * 资源包推送处理 Mixin
 * 
 * 拦截客户端接收服务器资源包推送的处理逻辑
 * 当原版资源包处理不经过 Meteor 收包事件时，在此处统一接管
 * 
 * 作用：将资源包推送事件转发给 ServerDetector 模块处理
 * 影响范围：所有服务器发送的资源包推送请求
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackPushMixin {
    
    /** 发送数据包的方法引用 */
    @Shadow public abstract void send(Packet<?> packet);

    /**
     * 拦截资源包推送处理
     * 
     * 注入点：handleResourcePackPush 方法头部
     * 效果：让 ServerDetector 模块接管资源包推送逻辑，可以取消原版处理
     * 
     * @param packet 服务器发送的资源包推送数据包
     * @param ci 回调信息，用于取消原版处理
     */
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
