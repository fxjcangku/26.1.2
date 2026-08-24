package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.commands.commands.ToggleCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ToggleCommand.class, remap = false)
public abstract class ToggleCommandTranslationMixin {
    
    // 拦截所有要输出的字符串并翻译
    @ModifyVariable(
        method = "build",
        at = @At("STORE"),
        ordinal = 0
    )
    private String yiyiaddon$translateMessages(String text) {
        if (!YiyiaddonTranslator.enabled() || text == null) return text;
        
        return text
            // Toggle 消息
            .replace("Toggled", "已切换")
            .replace(" on.", " 为开启。")
            .replace(" off.", " 为关闭。")
            // 错误消息
            .replace("Module not found.", "未找到该模块。")
            .replace("Invalid module.", "无效的模块。")
            .replace("You must specify a module.", "你必须指定一个模块。")
            // 按键绑定相关
            .replace("Binds a specified module to the next pressed key.", "将指定模块绑定到下一个按下的按键。")
            .replace("Bound to", "已绑定到")
            .replace("Unbound", "已解除绑定")
            // 其他通用
            .replace("Click to goto death", "点击前往死亡位置")
            .replace("Death position saved.", "死亡位置已保存。");
    }
}
