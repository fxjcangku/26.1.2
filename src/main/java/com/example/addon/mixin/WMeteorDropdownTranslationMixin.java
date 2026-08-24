package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown.class, remap = false)
public abstract class WMeteorDropdownTranslationMixin {
    @Redirect(method = "onRender", at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"))
    private String yiyiaddon$translateSelectedValue(Object value) {
        return YiyiaddonTranslator.translateSettingValue(value);
    }
}
