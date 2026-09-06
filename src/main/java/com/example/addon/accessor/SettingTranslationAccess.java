package com.example.addon.accessor;

/**
 * 设置项翻译访问器接口
 * 
 * 用于运行时修改 Meteor 设置项（Setting）的标题和描述，实现中文化
 * 配合 SettingTranslationMixin 使用
 */
public interface SettingTranslationAccess {
    /**
     * 设置标题
     * 
     * @param value 新的标题文本
     */
    void yiyiaddon$setTitle(String value);
    
    /**
     * 设置描述
     * 
     * @param value 新的描述文本
     */
    void yiyiaddon$setDescription(String value);
}
