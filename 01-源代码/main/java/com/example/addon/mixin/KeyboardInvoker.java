package com.example.addon.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 键盘处理器调用器 Mixin。
 *
 * 暴露 KeyboardHandler 的私有方法 keyPress(long, int, KeyEvent)，
 * 供自动登入模块的乐源服「Shift＋F 快捷键兜底」模拟真实按键事件。
 *
 * 1.20.4 版本中对应方法为 Keyboard.onKey(long, int, int, int, int)（公开），
 * 26.1.2 已重构为 KeyboardHandler.keyPress(long window, int action, KeyEvent event)（私有），
 * 参数由 (window, key, scancode, action, modifiers) 合并为 (window, action, KeyEvent(key, scancode, modifiers))。
 *
 * 用法：
 *   ((KeyboardInvoker) mc.keyboardHandler).yiyiaddon$keyPress(window, action, new KeyEvent(key, scancode, modifiers));
 *
 * 影响范围：不修改游戏行为，仅提供访问接口。
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardInvoker {

    /**
     * 调用器方法：注入一次键盘事件。
     *
     * @param window GLFW 窗口句柄（mc.getWindow().handle()）
     * @param action 按键动作：GLFW_PRESS(1) / GLFW_RELEASE(0)
     * @param event  按键事件（key / scancode / modifiers）
     */
    @Invoker("keyPress")
    void yiyiaddon$keyPress(long window, int action, KeyEvent event);
}
