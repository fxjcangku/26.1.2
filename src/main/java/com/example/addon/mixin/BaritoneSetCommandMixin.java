package com.example.addon.mixin;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.utils.SettingsUtil;
import baritone.command.defaults.SetCommand;
import com.example.addon.BaritoneSettingTranslations;
import com.example.addon.BaritoneSettingTranslations.Translation;
import com.example.addon.YiyiaddonTranslator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SetCommand.class, remap = false)
public abstract class BaritoneSetCommandMixin {
    @Inject(
        method = "lambda$execute$4(Lbaritone/api/Settings$Setting;)Lnet/minecraft/network/chat/Component;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void yiyiaddon$localizeSettingEntry(Settings.Setting<?> setting, CallbackInfoReturnable<Component> info) {
        if (!YiyiaddonTranslator.enabled()) return;

        String key = setting.getName();
        Translation translation = BaritoneSettingTranslations.get(key);
        String type = SettingsUtil.settingTypeToString(setting);
        String value = SettingsUtil.settingValueToString(setting);
        String defaultValue = SettingsUtil.settingDefaultToString(setting);

        MutableComponent typeText = Component.literal(" (" + type + ")")
            .withStyle(ChatFormatting.DARK_GRAY);
        MutableComponent hoverText = Component.literal(translation.name() + " (" + key + ")")
            .withStyle(ChatFormatting.GRAY)
            .append("\n\n说明：\n" + translation.description())
            .append("\n\n类型：" + type)
            .append("\n\n当前值：\n" + value)
            .append("\n\n默认值：\n" + defaultValue)
            .append("\n\n真实键：" + key);
        String suggestion = IBaritoneChatControl.FORCE_COMMAND_PREFIX
            + Baritone.settings().prefix.value
            + "set "
            + key;
        MutableComponent result = Component.literal(translation.name())
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(" [" + key + "]").withStyle(ChatFormatting.WHITE))
            .append(typeText);
        result.setStyle(result.getStyle()
            .withHoverEvent(new HoverEvent.ShowText(hoverText))
            .withClickEvent(new ClickEvent.SuggestCommand(suggestion)));
        info.setReturnValue(result);
    }
}
