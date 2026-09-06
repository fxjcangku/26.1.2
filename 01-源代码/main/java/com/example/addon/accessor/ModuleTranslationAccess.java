package com.example.addon.accessor;

// Accessor 接口：提供 ModuleTranslationMixin 注入的方法签名
// 用于运行时修改 Meteor 模块的标题和描述，实现中文化
public interface ModuleTranslationAccess {
    void yiyiaddon$setTitle(String value);
    void yiyiaddon$setDescription(String value);
}
