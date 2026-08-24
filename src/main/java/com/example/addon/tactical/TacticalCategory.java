package com.example.addon.tactical;

import meteordevelopment.meteorclient.systems.modules.Category;
import net.minecraft.world.item.Items;

/**
 * yiyiaddon 反作弊绕过模块分类
 * 
 * 专注于服务器反作弊绕过、网络拦截、协议欺骗
 * 
 * @author yiyijia
 */
public class TacticalCategory {
    
    public static final Category TACTICAL = new Category(
        "yiyiaddon 绕过",
        () -> Items.ENDER_EYE.getDefaultInstance()
    );
    
}
