package com.example.addon.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * 作物图鉴：把 PRD 里的四大类作物固化成数据表，供状态机查询。
 *
 * 设计要点：
 * · 成熟判定不硬编码 MAX_AGE 常量。26.1.2 里 SugarCaneBlock 根本不暴露 MAX_AGE，
 *   而 CropBlock.AGE（上限 7）和 BeetrootBlock.AGE（上限 3）是两套不同的属性对象，
 *   所以统一改成"从 BlockState 自身的属性表里找 age，取其可能值的最大值比对"，通吃全部作物。
 * · 柱状物没有可读的成熟度，只能靠"根部之上是否已长出"判定，
 *   收割点固定取根部往上第一格（PRD 的 Y+1 切割），砍掉后上方整根连带掉落。
 * · 附带掉落物（extraLoot）走白名单但不进安全库存截留，典型例子是土豆的毒马铃薯：
 *   必须被认领并全部倒进卸货箱，绝不能被当成种子留在背包里。
 *
 * ══════════════════════════════════════════════════════════════════
 *  ▌ 如何新增一种作物（只改这一个文件，UI 与状态机自动识别）
 * ══════════════════════════════════════════════════════════════════
 *  1. 在下方对应的 Kind 分区里加一行枚举，六个参数依次为：
 *     显示名、形态、方块、种子物品、主产物、底盘方块
 *  2. 柱状物（PILLAR）与蔓生物（VINE）不参与补种，种子与底盘传 null
 *  3. 若该作物有额外掉落（如土豆掉毒马铃薯），用带 extraLoot 的构造重载
 *  4. 完成。模块 UI 的作物勾选项、卸货白名单、扫描器全都是按 values() 遍历生成的，
 *     不需要改任何其他文件
 */
public enum CropProfile {

    // ── 📦 双作物：果实卸货，种子回收留作补种 ──
    WHEAT("小麦", Kind.TWIN, Blocks.WHEAT, Items.WHEAT_SEEDS, Items.WHEAT, Blocks.FARMLAND),
    BEETROOT("甜菜", Kind.TWIN, Blocks.BEETROOTS, Items.BEETROOT_SEEDS, Items.BEETROOT, Blocks.FARMLAND),

    // ── 🥔 单作物：产物即种子，卸货时按阈值截留 ──
    // 土豆有 2% 概率额外掉毒马铃薯，进白名单但不截留，全部倒进卸货箱
    POTATO("土豆", Kind.SINGLE, Blocks.POTATOES, Items.POTATO, Items.POTATO, Blocks.FARMLAND,
        Set.of(Items.POISONOUS_POTATO)),
    CARROT("胡萝卜", Kind.SINGLE, Blocks.CARROTS, Items.CARROT, Items.CARROT, Blocks.FARMLAND),
    NETHER_WART("地狱疣", Kind.SINGLE, Blocks.NETHER_WART, Items.NETHER_WART, Items.NETHER_WART, Blocks.SOUL_SAND),

    // ── 🎍 柱状物：Y+1 切割保留根部，全卸货，无补种 ──
    BAMBOO("竹子", Kind.PILLAR, Blocks.BAMBOO, null, Items.BAMBOO, null),
    SUGAR_CANE("甘蔗", Kind.PILLAR, Blocks.SUGAR_CANE, null, Items.SUGAR_CANE, null),
    CACTUS("仙人掌", Kind.PILLAR, Blocks.CACTUS, null, Items.CACTUS, null),

    // ── 🎃 蔓生物：只砍果实，全卸货，无补种 ──
    PUMPKIN("南瓜", Kind.VINE, Blocks.PUMPKIN, null, Items.PUMPKIN, null),
    MELON("西瓜", Kind.VINE, Blocks.MELON, null, Items.MELON_SLICE, null);

    /** 作物形态分类，决定状态机的收割与补种策略 */
    public enum Kind {
        /** 双作物：收获同时掉果实和种子 */
        TWIN,
        /** 单作物：产物本身就是种子 */
        SINGLE,
        /** 柱状物：垂直生长，切根部上方 */
        PILLAR,
        /** 蔓生物：果实为独立方块，砍完由茎再生 */
        VINE
    }

    private final String displayName;
    private final Kind kind;
    private final Block block;
    /** 补种用的种子物品，柱状物与蔓生物为 null */
    private final Item seed;
    /** 卸货白名单的主产物 */
    private final Item produce;
    /** 播种所需的底盘方块，柱状物与蔓生物为 null（不参与补种） */
    private final Block soil;
    /** 附带掉落物：进卸货白名单，但永不截留（如毒马铃薯） */
    private final Set<Item> extraLoot;

    CropProfile(String displayName, Kind kind, Block block, Item seed, Item produce, Block soil) {
        this(displayName, kind, block, seed, produce, soil, Set.of());
    }

    CropProfile(String displayName, Kind kind, Block block, Item seed, Item produce, Block soil, Set<Item> extraLoot) {
        this.displayName = displayName;
        this.kind = kind;
        this.block = block;
        this.seed = seed;
        this.produce = produce;
        this.soil = soil;
        this.extraLoot = extraLoot;
    }

    public String displayName() {
        return displayName;
    }

    public Kind kind() {
        return kind;
    }

    public Block block() {
        return block;
    }

    public Item seed() {
        return seed;
    }

    public Item produce() {
        return produce;
    }

    public Block soil() {
        return soil;
    }

    /** 是否需要副手种子参与补种。柱状物与蔓生物按 PRD 完全阉割补种。 */
    public boolean needsSeed() {
        return seed != null && (kind == Kind.TWIN || kind == Kind.SINGLE);
    }

    /**
     * 卸货时需要截留的种子物品。
     * 单作物产物即种子，必须截留安全库存；双作物截留的是独立的种子物品。
     */
    public Item retainItem() {
        return needsSeed() ? seed : null;
    }

    /** 附带掉落物（毒马铃薯之类），进白名单但不截留 */
    public Set<Item> extraLoot() {
        return extraLoot;
    }

    /**
     * 该作物贡献到卸货白名单的全部物品。
     * 双作物收获会同时掉果实和种子，两者都要进白名单才能被拾取阶段认领；
     * 附带掉落物同样进白名单，但在截留判定里不算种子，会被整堆倒进卸货箱。
     */
    public Set<Item> lootWhitelist() {
        Set<Item> whitelist = new java.util.HashSet<>();
        whitelist.add(produce);
        if (seed != null) whitelist.add(seed);
        whitelist.addAll(extraLoot);
        return Collections.unmodifiableSet(whitelist);
    }

    /**
     * 判定给定坐标是否为可收割目标。
     *
     * @param state 该坐标的方块状态，调用方需保证 block 已匹配
     * @param level 世界访问器，柱状物需要回溯下方方块
     * @param pos   目标坐标
     */
    public boolean isHarvestable(BlockState state, BlockGetter level, BlockPos pos) {
        if (!state.is(block)) return false;

        return switch (kind) {
            // 果实方块存在即可收割
            case VINE -> true;
            // 取根部往上第一格：下方是同种（说明自己不是根），再下一格不是同种（说明下方就是根）
            case PILLAR -> {
                BlockPos below = pos.below();
                if (!level.getBlockState(below).is(block)) yield false;
                yield !level.getBlockState(below.below()).is(block);
            }
            // 农作物读 age 属性
            case TWIN, SINGLE -> isMaxAge(state);
        };
    }

    /**
     * 判定给定坐标是否为待补种的空地：底盘正确且其上方为空气。
     *
     * @param level 世界访问器
     * @param soilPos 底盘坐标（作物将种在其上方）
     */
    public boolean isPlantable(BlockGetter level, BlockPos soilPos) {
        if (!needsSeed() || soil == null) return false;
        if (!level.getBlockState(soilPos).is(soil)) return false;
        return level.getBlockState(soilPos.above()).isAir();
    }

    /**
     * 通用成熟度判定：在方块状态自身的属性表里找整型 age 属性，
     * 取其可能值上限与当前值比对。避免依赖各作物类各不相同的 MAX_AGE 常量。
     */
    public static boolean isMaxAge(BlockState state) {
        IntegerProperty age = findAgeProperty(state);
        if (age == null) return false;

        int max = Integer.MIN_VALUE;
        for (int value : age.getPossibleValues()) {
            if (value > max) max = value;
        }
        return max != Integer.MIN_VALUE && state.getValue(age) >= max;
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        Collection<Property<?>> properties = state.getProperties();
        for (Property<?> property : properties) {
            if (property instanceof IntegerProperty integerProperty && "age".equals(property.getName())) {
                return integerProperty;
            }
        }
        return null;
    }

    /** 按方块反查图鉴条目，未收录返回 null。 */
    public static CropProfile byBlock(Block block) {
        for (CropProfile profile : values()) {
            if (profile.block == block) return profile;
        }
        return null;
    }

    /** 竹笋是竹子的根部形态，扫描时需要一并识别以免误判为空地。 */
    public static boolean isPillarRoot(BlockState state) {
        return state.is(Blocks.BAMBOO_SAPLING);
    }

    /** 图鉴内全部作物的不可变视图，供 UI 生成勾选项。 */
    public static Collection<CropProfile> all() {
        return Collections.unmodifiableCollection(java.util.Arrays.asList(values()));
    }
}
