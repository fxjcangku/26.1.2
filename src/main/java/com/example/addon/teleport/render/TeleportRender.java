package com.example.addon.teleport.render;

import com.example.addon.teleport.model.TeleportContext;
import com.example.addon.teleport.model.TeleportTarget;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 调试渲染层：只画最近一次传送的目标点、穿墙射线、回弹点与候选格，
 * 本身不参与任何业务决策（与 FarmRenderer 同原则）。
 */
public final class TeleportRender {

    /** 目标落点：绿色线框 */
    private static final Color TARGET_LINE = new Color(0, 255, 120, 255);
    private static final Color TARGET_SIDE = new Color(0, 255, 120, 40);

    /** 穿墙射线：亮青 */
    private static final Color RAY = new Color(0, 200, 255, 200);

    /** 回弹点：红色 */
    private static final Color RUBBER = new Color(255, 80, 80, 255);

    /** 安全搜索候选格：黄色淡框 */
    private static final Color CELL = new Color(255, 255, 0, 60);

    /** 移动对象碰撞箱（玩家 / 载具+乘客）：橙色线框 */
    private static final Color SUBJECT_LINE = new Color(255, 160, 40, 200);
    private static final Color SUBJECT_SIDE = new Color(255, 160, 40, 40);

    private TeleportRender() {
    }

    /** 渲染最近一次传送上下文（debugDraw 开关打开时由模块调用） */
    public static void draw(Render3DEvent event, TeleportContext last) {
        if (last == null) return;

        // 候选格（安全搜索评估记录）
        for (BlockPos cell : last.debugCells) {
            event.renderer.box(cell, CELL, CELL, ShapeMode.Lines, 0);
        }

        // 穿墙锁定射线：始终画到「最大搜索距离」终点，表达搜索范围边界；
        // 实际选中目标由下方绿色线框单独标识，二者分离避免「必须传到最远」的错觉
        if (last.rayOrigin != null && last.rayDir != null) {
            Vec3 end = last.rayOrigin.add(last.rayDir.scale(last.rayLength));
            event.renderer.line(last.rayOrigin.x(), last.rayOrigin.y(), last.rayOrigin.z(),
                end.x(), end.y(), end.z(), RAY);
        }

        // 目标落点（玩家身高线框）
        if (last.target != null) {
            boxFor(event, last.target, TARGET_SIDE, TARGET_LINE);
        }

        // 移动对象实际碰撞箱（玩家 / 载具+乘客）：调试辅助判断大型载具是否容纳
        if (last.subjectBoxes != null) {
            for (AABB box : last.subjectBoxes) {
                event.renderer.box(box, SUBJECT_SIDE, SUBJECT_LINE, ShapeMode.Lines, 0);
            }
        }

        // 服务器回弹点
        if (last.rubberbandPos != null) {
            AABB box = new AABB(
                last.rubberbandPos.add(-0.3, 0, -0.3),
                last.rubberbandPos.add(0.3, 1.8, 0.3));
            event.renderer.box(box, RUBBER, RUBBER, ShapeMode.Lines, 0);
        }
    }

    /** 目标落点玩家身高线框 */
    private static void boxFor(Render3DEvent event, TeleportTarget target, Color side, Color line) {
        AABB box = new AABB(
            target.feet().add(-0.3, 0, -0.3),
            target.feet().add(0.3, 1.8, 0.3));
        event.renderer.box(box, side, line, ShapeMode.Lines, 0);
    }
}