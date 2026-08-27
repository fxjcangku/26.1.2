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

/**
 * Meteor 模块分类翻译 Mixin
 * 
 * 拦截 Category 构造函数，将英文分类名转换为中文
 * 影响范围：模块列表左侧的分类标签
 */
@Mixin(value = Category.class, remap = false)
public abstract class CategoryTranslationMixin {
    
    @Mutable 
    @Shadow 
    @Final 
    public String name;

    /**
     * 在构造函数结束时替换分类名为中文
     */
    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"))
    private void yiyiaddon$localize(String value, CallbackInfo info) {
        name = YiyiaddonTranslator.localizeCategory(name);
    }
}
