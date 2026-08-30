package com.example.addon.villager.logistics;

import com.example.addon.farm.ContainerBroker;
import com.example.addon.villager.data.VillagerTradeTarget;
import com.example.addon.villager.trade.EnchantmentMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 任务物品卸货搬运器（前提：成品交易箱界面已由状态机打开）
 * 
 * 核心原则：
 * · 只卸载当前任务目标物品，不触碰玩家私人物品（装备 / 工具 / 食物 / 绿宝石）
 * · 附魔书必须精准匹配附魔类型
 * · 槽位操作走 ContainerBroker（按 BPT 节流，且正确处理 menu 与背包槽位映射，
 *   不再手写槽位偏移公式）
 * 
 * 状态：
 * IDLE → RUNNING → COMPLETED / ERROR
 */
public final class UnloadService {

    private final Minecraft mc;
    private final ContainerBroker broker;
    private Consumer<String> logger;

    private State state = State.IDLE;
    private List<VillagerTradeTarget> taskTargets = new ArrayList<>();
    private int opCooldown = 0;        // 槽位操作节流（tick）
    private int failStreak = 0;        // 连续操作失败计数
    private int readyTimeout = 0;      // 容器同步等待超时

    private static final int OP_INTERVAL = 3;        // 每 3 tick 最多一次槽位操作
    private static final int READY_TIMEOUT = 100;    // 容器同步 5 秒超时
    private static final int DONE_STREAK_LIMIT = 6;  // 连续无货可卸判完成

    public UnloadService() {
        this.mc = Minecraft.getInstance();
        this.broker = new ContainerBroker();
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * 开始卸货搬运。
     *
     * @param targets 当前任务目标列表，只卸载命中白名单的物品
     */
    public void start(List<VillagerTradeTarget> targets) {
        this.taskTargets = new ArrayList<>(targets);
        this.opCooldown = 0;
        this.failStreak = 0;
        this.readyTimeout = 0;
        broker.reset();
        state = State.RUNNING;
        log("§b开始卸货");
    }

    /**
     * 由状态机在箱子界面打开后每 tick 调用。
     */
    public void tick() {
        if (state != State.RUNNING) return;

        LocalPlayer player = mc.player;
        if (player == null) {
            state = State.ERROR;
            return;
        }

        broker.tick();
        if (!broker.isReady()) {
            if (++readyTimeout > READY_TIMEOUT) {
                log("§c容器同步超时");
                state = State.ERROR;
            }
            return;
        }

        if (opCooldown > 0) {
            opCooldown--;
            return;
        }
        opCooldown = OP_INTERVAL;

        if (broker.depositOne(this::isTaskTarget)) {
            failStreak = 0;
            return;
        }

        // 连续搬不动：背包里已没有任务物品（或箱子满），判定完成
        if (++failStreak >= DONE_STREAK_LIMIT) {
            log("§a卸货完成");
            state = State.COMPLETED;
        }
    }

    /**
     * 背包是否还存在待卸任务物品（供状态机决定要不要走卸货流程）。
     */
    public boolean hasTaskItems(List<VillagerTradeTarget> targets) {
        LocalPlayer player = mc.player;
        if (player == null || targets.isEmpty()) return false;

        var inventory = player.getInventory();
        List<VillagerTradeTarget> checkTargets = new ArrayList<>(targets);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isMatchingSheet(stack, checkTargets)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 ItemStack 是否命中任务白名单。
     */
    private boolean isTaskTarget(ItemStack stack) {
        return isMatchingSheet(stack, taskTargets);
    }

    private static boolean isMatchingSheet(ItemStack stack, List<VillagerTradeTarget> targets) {
        if (stack.isEmpty()) return false;

        for (VillagerTradeTarget target : targets) {
            if (target.getItem() != stack.getItem()) continue;

            // 附魔书必须精准匹配附魔类型，普通物品 Item 一致即可
            if (target.isEnchantedBook()) {
                if (EnchantmentMatcher.matches(stack, target)) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    public void reset() {
        state = State.IDLE;
        opCooldown = 0;
        failStreak = 0;
        readyTimeout = 0;
        taskTargets.clear();
        broker.reset();
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    public enum State {
        IDLE,
        RUNNING,
        COMPLETED,
        ERROR
    }
}