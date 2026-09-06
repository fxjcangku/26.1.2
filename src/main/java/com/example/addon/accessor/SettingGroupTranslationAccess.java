package com.example.addon.accessor;

/**
 * SettingGroup 翻译访问器接口。
 * 用于运行时修改 SettingGroup 的显示名称，实现设置分组标题的中文化。
 */
public interface SettingGroupTranslationAccess {
    /** 运行时改写设置分组的名称 */
    void yiyiaddon$setName(String value);
}
