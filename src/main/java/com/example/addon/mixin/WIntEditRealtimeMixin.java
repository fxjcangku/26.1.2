package com.example.addon.mixin;

import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WIntEdit 文本框实时输入 Mixin
 *
 * Meteor 原生 WIntEdit 的文本框只在失焦（回车/点击别处）时才把文本写回 value，
 * noSlider 模式（只有 +/− 按钮）下用户点框手输数字后看不到任何反馈，误以为不能改。
 * 这里把文本框改成实时解析并夹取到 [min, max]，输入即生效。
 */
@Mixin(value = WIntEdit.class, remap = false)
public abstract class WIntEditRealtimeMixin {

    @Shadow private int value;
    @Shadow private WTextBox textBox;
    @Shadow private WSlider slider;
    @Shadow public Runnable action;
    @Shadow @Final public int min;
    @Shadow @Final public int max;

    @Inject(method = "init", at = @At("TAIL"))
    private void yiyiaddon$realtimeTextBox(CallbackInfo ci) {
        textBox.action = () -> {
            String s = textBox.get();
            // 空串或单独负号属于中间输入态，等用户继续输入
            if (s.isEmpty() || s.equals("-")) return;

            int parsed;
            try {
                parsed = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return;
            }

            int clamped = Math.max(min, Math.min(max, parsed));
            if (clamped == value) return;

            value = clamped;
            if (slider != null) slider.set(value);
            if (action != null) action.run();
        };
    }
}
