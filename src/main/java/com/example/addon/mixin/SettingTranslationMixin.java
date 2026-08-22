package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Setting.class, remap = false)
public abstract class SettingTranslationMixin {
    @Shadow @Mutable public String title;
    @Shadow @Mutable public String description;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void yiyiaddon$translate(String name, String description, Object defaultValue, Consumer<?> onChanged,
                                     Consumer<?> onModuleActivated, IVisible visible, CallbackInfo ci) {
        title = YiyiaddonTranslator.translate("Setting.Meteor." + name, title);
        this.description = YiyiaddonTranslator.translate("Setting.Meteor." + name + ".Description", this.description);
    }
}
