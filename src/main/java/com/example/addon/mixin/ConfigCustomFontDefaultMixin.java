package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = Config.class, remap = false)
public abstract class ConfigCustomFontDefaultMixin {
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 1, ordinal = 0))
    private int yiyiaddon$disableCustomFontByDefault(int value) {
        return 0;
    }
}
