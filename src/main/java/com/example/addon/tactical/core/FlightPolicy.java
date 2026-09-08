package com.example.addon.tactical.core;

/**
 * 飞行策略词汇表（L1 协调器与 L2 移动执行器共用的类型）。
 *
 * 职责边界：模式枚举是「执行词汇」，最终执行哪个模式由 TacticalCoordinator
 * （唯一决策点）根据检测结果 + 冷却 + 拉回统计产出 FlightDecision，
 * 移动执行器（FlightBypass）只负责按决策执行，不允许自行换模式。
 *
 * 26.1.2 官方机制依据（ServerGamePacketListenerImpl）：
 * - 原版浮空判定只认「物理支撑」：verticalCollisionBelow 为真、或脚下 0.55 格
 *   内有方块（noBlocksAround 为假）、或玩家合法飞行（abilities/mayfly、
 *   fallyFlying 鞘翅滑翔、悬浮药水、旁观）才豁免浮空踢；
 * - 因此「无鞘翅无权限的悬停类飞行」在 26.1.2 服务端必定 80 tick 被踢，
 *   发包飞行（PACKET_FLY）必须由协调器校验服务端飞行权限后才能放行。
 *
 * @author yiyijia
 */
public final class FlightPolicy {

    private FlightPolicy() {
    }

    /** 飞行模式（显示名与配置存档名保持一致，迁移时用户配置不丢失） */
    public enum FlightMode {
        /** 发包飞行：本地 velocity 直控。前提是服务端授予飞行能力（/fly、创造、旁观），否则原版浮空踢无法豁免 */
        PACKET_FLY("发包飞行"),

        /** 原版模拟：落地即起跳的原版跳跃弧线，每跳都靠地面接触重置浮空计时，无需任何作弊前提 */
        VANILLA_MIMIC("原版模拟"),

        /** 安全滑翔：自动开鞘翅合法滑翔（fallFlying 豁免浮空判定，且服务端超速容忍提升到 300 m/t） */
        SAFE_GLIDE("安全滑翔"),

        /** 烟花火箭：鞘翅滑翔中周期性使用烟花推进，服务端完全合法 */
        FIREWORK_BOOST("烟花火箭"),

        /** 序列垫脚：用方块在脚下创造真实支撑面，方块接触豁免浮空判定 */
        SEQUENCE_SCAFFOLD("序列垫脚");

        /** 模式中文显示名 */
        public final String displayName;

        FlightMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** 决策原因（决定移动执行器本 tick 是否执行、执行什么） */
    public enum FlightReason {
        /** 允许执行当前模式 */
        GRANTED,

        /** 拉回冷却期：全部模式统一暂停，防止顶风作案（静默，不播报） */
        COOLDOWN,

        /** 高风险反作弊 + 发包飞行：直接拒绝执行（服务器浮空踢无法豁免） */
        HIGH_RISK_AC,

        /** 发包飞行未获服务端飞行权限：决策携带沿降级链落下的替代模式（安全滑翔/原版模拟），执行器执行替代模式而非停摆 */
        NO_FLY_ABILITY,

        /** 连续拉回触发自适应降级：按降级链执行更保守的模式 */
        DEGRADED
    }

    /**
     * 飞行决策：协调器每 tick 产出的唯一执行依据。
     *
     * @param mode   本 tick 实际应执行的模式（降级时为目标模式）
     * @param reason 决策原因
     */
    public record FlightDecision(FlightMode mode, FlightReason reason) {

        /**
         * 是否允许执行移动注入。
         * 降级与无权限回落都算允许（只是换模式执行），只有高风险拒绝与拉回冷却停摆。
         */
        public boolean granted() {
            return reason == FlightReason.GRANTED
                || reason == FlightReason.DEGRADED
                || reason == FlightReason.NO_FLY_ABILITY;
        }
    }
}