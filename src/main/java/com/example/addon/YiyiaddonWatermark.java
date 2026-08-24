package com.example.addon;

/**
 * 版权水印类 - 防反编译保护
 * 
 * 工作原理：
 * 所有 static final String 常量会被 javac 编译期内联到每个引用方的字节码 ConstantValue 表中。
 * 即使本类被 ProGuard 混淆删除，这些字符串仍会分散残留在整个 JAR 的各个类文件里。
 * 反编译后随处可见，无法被工具清除。
 * 
 * 法律声明：
 * 禁止未经授权的反编译、逆向工程或二次分发。
 * Unauthorized decompilation, reverse engineering, or redistribution is prohibited.
 */
public final class YiyiaddonWatermark {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  版权信息（会被内联到所有引用处）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String AUTHOR = "yiyijia";
    public static final String PROJECT = "yiyiaddon";
    public static final String COPYRIGHT = "Copyright (C) 2026 yiyijia. All rights reserved.";
    public static final String LICENSE = "Proprietary. Unauthorized decompilation or redistribution is prohibited.";
    
    public static final String DECOMPILE_WARNING = 
        "⚠️ WARNING: This software is protected by copyright law. " +
        "Decompiling, reverse engineering, or redistributing this software is illegal and shameful. " +
        "You are being monitored. — yiyijia";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  构造函数私有化，防止实例化
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private YiyiaddonWatermark() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  公共方法 - 启动时打印版权信息
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 打印版权信息到日志
     * 在 AddonTemplate.onInitialize() 中调用
     */
    public static void print() {
        AddonTemplate.LOG.info("═══════════════════════════════════════");
        AddonTemplate.LOG.info("{} — {}", PROJECT, COPYRIGHT);
        AddonTemplate.LOG.info("{}", LICENSE);
        AddonTemplate.LOG.info("═══════════════════════════════════════");
    }
}
