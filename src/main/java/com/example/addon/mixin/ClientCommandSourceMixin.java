package com.example.addon.mixin;

import com.example.addon.utils.YiyiaddonCommandLogger;
import com.example.addon.utils.YiyiaddonIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截玩家发送的所有指令（以 / 开头），上报到后台用于功能使用统计。
 *
 * <p>注入点：{@link ClientPacketListener#sendCommand(String)}
 * <p>只记录指令名称，不包含参数、密码或坐标等敏感信息。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientCommandSourceMixin {

    /**
     * 拦截 sendCommand 方法，在指令发送前记录到后台。
     *
     * @param command 指令字符串（不含 / 前缀）
     * @param ci Mixin 回调信息
     */
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfo ci) {
        if (command == null || command.isBlank()) return;
        
        // 获取玩家身份信息
        Minecraft minecraft = Minecraft.getInstance();
        String uuid = YiyiaddonIdentity.uuid(minecraft);
        String name = YiyiaddonIdentity.name(minecraft);
        
        // 异步上报到后台（添加 / 前缀以保持统一格式）
        YiyiaddonCommandLogger.logCommand("/" + command, uuid, name);
    }
}
