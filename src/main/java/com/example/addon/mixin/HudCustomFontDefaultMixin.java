package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.hud.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = Hud.class, remap = false)
public abstract class HudCustomFontDefaultMixin {
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 1, ordinal = 0))
    private int yiyiaddon$disableCustomFontByDefault(int value) {
        return 0;
    }
}
