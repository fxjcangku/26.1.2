package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.systems.modules.Category;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Category.class, remap = false)
public abstract class CategoryTranslationMixin {
    @Mutable @Shadow @Final public String name;

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"))
    private void yiyiaddon$localize(String value, CallbackInfo info) {
        name = YiyiaddonTranslator.localizeCategory(name);
    }
}
