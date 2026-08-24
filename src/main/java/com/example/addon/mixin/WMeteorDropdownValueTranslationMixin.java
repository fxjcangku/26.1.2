package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown$WValue", remap = false)
public abstract class WMeteorDropdownValueTranslationMixin {
    @Redirect(method = "onCalculateSize", at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"))
    private String yiyiaddon$translateValueSize(Object value) {
        return YiyiaddonTranslator.translateSettingValue(value);
    }

    @Redirect(method = "onRender", at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"))
    private String yiyiaddon$translateValueText(Object value) {
        return YiyiaddonTranslator.translateSettingValue(value);
    }
}
