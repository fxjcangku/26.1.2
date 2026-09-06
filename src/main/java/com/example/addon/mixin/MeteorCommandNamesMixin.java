package com.example.addon.mixin;

import com.example.addon.translations.MeteorCommandTranslations;
import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;

/**
 * 支持 Meteor 中文指令输入
 * 
 * 原理：拦截 get() 方法的参数，将中文指令名转换为英文
 * 效果：用户可以输入 .帮助 或 .help，都能找到对应的指令
 */
@Mixin(value = Commands.class, remap = false)
public abstract class MeteorCommandNamesMixin {
    
    @ModifyVariable(
        method = "get(Ljava/lang/String;)Lmeteordevelopment/meteorclient/commands/Command;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private static String yiyiaddon$translateChineseToEnglish(String name) {
        if (!YiyiaddonTranslator.enabled() || name == null) return name;
        
        String lowerName = name.toLowerCase(Locale.ROOT);
        String englishName = MeteorCommandTranslations.reverseTranslate(lowerName);
        
        // 如果找到了对应的英文名，返回英文名；否则返回原名
        return !englishName.equals(lowerName) ? englishName : name;
    }
}
