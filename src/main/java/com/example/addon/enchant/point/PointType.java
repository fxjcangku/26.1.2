package com.example.addon.enchant.point;

import java.util.List;

/**
 * 自动附魔 · 统一点位业务类型。
 *
 * <p>三种模式（原版装备 / 原版附魔书 / 自定义附魔）共享同一套点位业务类型，
 * GUI 按钮、.fumo 指令、启动自检、状态机全部通过本枚举访问同一个点位，
 * 禁止「箱子坐标」这类模糊类型，禁止为三种模式各建一套 PointType。</p>
 */
public enum PointType {

    /** 空白书箱（原版附魔书 / 自定义附魔） */
    BOOK_STORAGE("空白书箱", "书"),
    /** 青金石箱（原版附魔书 / 自定义附魔） */
    LAPIS_STORAGE("青金石箱", "青晶石"),
    /** 工具 / 护甲箱（原版装备极品附魔） */
    EQUIPMENT_STORAGE("工具/护甲箱", "工具护甲箱"),
    /** 附魔台（三模式共享） */
    ENCHANTING_TABLE("附魔台", "附魔台"),
    /** 砂轮（三模式共享） */
    GRINDSTONE("砂轮", "砂轮"),
    /** 铁砧（原版装备极品附魔） */
    ANVIL("铁砧", "铁砧"),
    /** 铁砧箱（原版装备极品附魔，存放备用铁砧） */
    ANVIL_BOX("铁砧箱", "铁砧箱"),
    /** 挂机点（三模式共享，复用现有 .fumo/AFK 体系） */
    AFK("挂机点", "挂机位"),
    /** 成品箱（三模式共享：附魔书成品 / 极品装备成品） */
    OUTPUT_STORAGE("成品箱", "成品箱"),
    /** 异常装备箱（原版装备极品附魔，存附魔失败 / 不达标 / 无法继续处理的装备） */
    ERROR_STORAGE("异常装备箱", "异常装备箱");

    /** 中文显示名 */
    private final String title;
    /** .fumo 指令节点字面量 */
    private final String node;

    PointType(String title, String node) {
        this.title = title;
        this.node = node;
    }

    /** 中文显示名 */
    public String title() {
        return title;
    }

    /** .fumo 指令节点字面量 */
    public String node() {
        return node;
    }

    @Override
    public String toString() {
        return title;
    }

    /** 按 .fumo 指令字面量解析 PointType，未匹配返回 null */
    public static PointType fromNode(String node) {
        for (PointType type : values()) {
            if (type.node.equals(node)) return type;
        }
        return null;
    }

    /** 所有点位类型 */
    public static List<PointType> all() {
        return List.of(values());
    }
}
