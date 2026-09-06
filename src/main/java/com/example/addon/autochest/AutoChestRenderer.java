package com.example.addon.autochest;

import com.example.addon.autochest.model.ChestTarget;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * AutoChest ESP 渲染器：高亮扫描到的合法容器。
 *
 * <p>三态着色：未处理绿色、已处理红色、处理中黄色（处理中绝不能显示为已处理红）。
 * 复用 Meteor 的 {@link Render3DEvent} 3D 渲染管线，只画框线不填充，
 * 距离过远（>64 格）不渲染。</p>
 */
public final class AutoChestRenderer {

    private static final double RENDER_DISTANCE = 64.0;

    private final Minecraft mc;
    private final AutoChestModule module;

    public AutoChestRenderer(Minecraft mc, AutoChestModule module) {
        this.mc = mc;
        this.module = module;
    }

    /** 每帧渲染（由模块 onRender3D 驱动） */
    public void render(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!module.moduleSettings.renderEsp.get()) return;

        List<ChestTarget> targets = module.scanner.results();
        if (targets.isEmpty()) return;

        long expireMs = module.moduleSettings.recordExpireMinutes.get() * 60_000L;
        ChestTarget processing = module.processingTarget();

        for (ChestTarget target : targets) {
            if (!target.inCurrentDimension()) continue;

            Vec3 playerPos = mc.player.getEyePosition(event.tickDelta);
            Vec3 targetPos = new Vec3(
                target.pos().getX() + 0.5,
                target.pos().getY() + 0.5,
                target.pos().getZ() + 0.5);
            if (playerPos.distanceTo(targetPos) > RENDER_DISTANCE) continue;

            Color color;
            if (isProcessing(target, processing)) {
                // 处理中：黄色，绝不能显示为已处理红
                color = module.moduleSettings.processingColor.get();
            } else if (module.recordManager.isProcessed(
                target.pos(), target.dimension(), target.containerType(), expireMs)) {
                color = module.moduleSettings.processedColor.get();
            } else {
                color = module.moduleSettings.unprocessedColor.get();
            }

            event.renderer.box(target.pos(), color, color, module.moduleSettings.espStyle.get().shapeMode, 0);
        }
    }

    /** 目标是否为当前处理中的容器（处理中与未处理/已处理区分开） */
    private boolean isProcessing(ChestTarget target, ChestTarget processing) {
        return processing != null
            && processing.pos().equals(target.pos())
            && processing.dimension().equals(target.dimension());
    }
}
