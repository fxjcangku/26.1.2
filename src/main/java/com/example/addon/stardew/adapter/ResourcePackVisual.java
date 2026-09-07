package com.example.addon.stardew.adapter;

/**
 * 资源包视觉信息占位：描述资源包提供的某类模型/纹理引用。
 *
 * <p>资源包是增强层，不是核心依赖。当前无资源包，字段仅作未来接入约定，不承载运行时核心判断。</p>
 */
public record ResourcePackVisual(
    String kind,
    String path,
    String displayName
) {
}
