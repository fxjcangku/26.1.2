package com.example.addon;

/**
 * 版权水印类。
 *
 * 所有 static final String 常量会被 javac 直接内联进
 * 每一个引用方的字节码 ConstantValue 表中。
 * 即使本类被混淆压缩，这些字符串仍会分散残留在整个 JAR 的字节码里，
 * 反编译后随处可见，无法被 ProGuard 删除。
 *
 * 禁止反编译、逆向工程或二次分发。
 * Unauthorized decompilation, reverse engineering, or redistribution is prohibited.
 */
public final class YiyiaddonWatermark {

    public static final String AUTHOR       = "yiyijia";
    public static final String PROJECT      = "yiyiaddon";
    public static final String COPYRIGHT    = "Copyright (C) yiyijia. All rights reserved.";
    public static final String LICENSE      = "Proprietary. Unauthorized decompilation or redistribution is prohibited.";
    public static final String DECOMPILE_WARNING =
        "WARNING: This software is protected. Decompiling is shameful and illegal. " +
        "You are being watched. — yiyijia";

    private YiyiaddonWatermark() {}

    public static void print() {
        AddonTemplate.LOG.info("[yiyiaddon] {} — {}", COPYRIGHT, LICENSE);
    }
}
