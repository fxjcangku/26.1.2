package com.example.addon.mixin;

import com.example.addon.core.AddonTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 示例类
 * 
 * 学习资源：
 * <ul>
 * <li><a href="https://fabricmc.net/wiki/tutorial:mixin_introduction">FabricMC Wiki</a></li>
 * <li><a href="https://github.com/SpongePowered/Mixin/wiki">Mixin Wiki</a></li>
 * <li><a href="https://github.com/LlamaLad7/MixinExtras/wiki">MixinExtras Wiki</a></li>
 * <li><a href="https://jenkins.liteloader.com/view/Other/job/Mixin/javadoc/allclasses-noframe.html">Mixin Javadoc</a></li>
 * <li><a href="https://github.com/2xsaiko/mixin-cheatsheet">Mixin 速查表</a></li>
 * </ul>
 */
@Mixin(Minecraft.class)
public abstract class ExampleMixin {
    
    /**
     * Mixin 注入示例
     * 目标：Minecraft 类的构造函数 <init>
     * 注入点：构造函数末尾 TAIL
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onGameLoaded(GameConfig gameConfig, CallbackInfo ci) {
        AddonTemplate.LOG.info("Hello from ExampleMixin!");
    }
}
