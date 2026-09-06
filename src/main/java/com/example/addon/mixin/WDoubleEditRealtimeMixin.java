package com.example.addon.mixin;

import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WDoubleEdit 文本框实时输入 Mixin
 *
 * 与 {@link WIntEditRealtimeMixin} 同理：noSlider 模式下文本框原本要失焦才生效，
 * 这里改为实时解析并夹取到 [min, max]，点框手输立即生效。
 */
@Mixin(value = WDoubleEdit.class, remap = false)
public abstract class WDoubleEditRealtimeMixin {

    @Shadow private double value;
    @Shadow private WTextBox textBox;
    @Shadow private WSlider slider;
    @Shadow public Runnable action;
    @Shadow @Final private double min;
    @Shadow @Final private double max;

    @Inject(method = "init", at = @At("TAIL"))
    private void yiyiaddon$realtimeTextBox(CallbackInfo ci) {
        textBox.action = () -> {
            String s = textBox.get();
            // 空串、单独负号、小数点等中间输入态，等用户继续输入
            if (s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")) return;

            double parsed;
            try {
                parsed = Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return;
            }

            double clamped = Math.max(min, Math.min(max, parsed));
            if (clamped == value) return;

            value = clamped;
            if (slider != null) slider.set(value);
            if (action != null) action.run();
        };
    }
}
