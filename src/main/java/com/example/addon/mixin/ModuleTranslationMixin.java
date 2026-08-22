package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleTranslationMixin {
    @Shadow @Mutable public String title;
    @Shadow @Mutable public String description;
    @Shadow public String name;

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void yiyiaddon$translate(Category category, String name, String description, String[] aliases, CallbackInfo ci) {
        title = YiyiaddonTranslator.translate("module." + name, title);
        this.description = YiyiaddonTranslator.translate("module." + name + ".description", this.description);
    }
}
