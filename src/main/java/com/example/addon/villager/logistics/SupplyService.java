package com.example.addon.villager.logistics;

import com.example.addon.farm.ContainerBroker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * 绿宝石补给搬运器（前提：绿宝石箱界面已由状态机打开）
 * 
 * 职责收窄为「箱子已开后，按节流节奏把绿宝石搬进背包」，
 * 开箱 / 关闭 / 寻路由状态机负责，本类不再碰这些环节。
 * 
 * 状态：
 * IDLE → RUNNING → COMPLETED / ERROR
 */
public final class SupplyService {

    private final Minecraft mc;
    private final ContainerBroker broker;
    private Consumer<String> logger;

    private State state = State.IDLE;
    private int targetEmeralds = 64;      // 本次补给目标总量
    private int opCooldown = 0;           // 槽位操作节流（tick）
    private int failStreak = 0;           // 连续操作失败计数
    private int readyTimeout = 0;         // 容器同步等待超时

    private static final int OP_INTERVAL = 2;        // 每 2 tick 最多一次槽位操作
    private static final int READY_TIMEOUT = 100;    // 容器同步 5 秒超时
    private static final int EMPTY_STREAK_LIMIT = 8; // 连续取不出绿宝石判箱空

    public SupplyService() {
        this.mc = Minecraft.getInstance();
        this.broker = new ContainerBroker();
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * 开始补给搬运。
     *
     * @param targetEmeralds 背包绿宝石达到该数量即完成
     */
    public void start(int targetEmeralds) {
        this.targetEmeralds = Math.max(1, targetEmeralds);
        this.opCooldown = 0;
        this.failStreak = 0;
        this.readyTimeout = 0;
        broker.reset();
        state = State.RUNNING;
        log("§b开始搬运绿宝石，目标 " + targetEmeralds + " 个");
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

        // 容器同步观测必须先于就绪判定
        broker.tick();
        if (!broker.isReady()) {
            if (++readyTimeout > READY_TIMEOUT) {
                log("§c容器同步超时");
                state = State.ERROR;
            }
            return;
        }

        // 背包绿宝石已达目标
        if (countEmeralds(player) >= targetEmeralds) {
            log("§a补给达标（" + countEmeralds(player) + " 个）");
            state = State.COMPLETED;
            return;
        }

        // 节流
        if (opCooldown > 0) {
            opCooldown--;
            return;
        }
        opCooldown = OP_INTERVAL;

        // 背包满则无法继续取
        if (player.getInventory().getFreeSlot() == -1) {
            log("§e背包已满，补给提前结束");
            state = State.COMPLETED;
            return;
        }

        if (broker.withdrawOne(Items.EMERALD)) {
            failStreak = 0;
            return;
        }

        // 连续取不出：判定箱子是不是空了
        if (++failStreak >= EMPTY_STREAK_LIMIT) {
            if (countInChest() == 0) {
                log("§e绿宝石箱已空，补给提前结束");
                state = State.COMPLETED;
            } else {
                log("§c补给搬运异常");
                state = State.ERROR;
            }
        }
    }

    private int countInChest() {
        AbstractContainerMenu menu = ContainerBroker.openMenu();
        if (menu == null) return 0;
        return ContainerBroker.countInChest(menu, Items.EMERALD);
    }

    private int countEmeralds(LocalPlayer player) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.EMERALD) {
                count += stack.getCount();
            }
        }
        // 副手也计入：绿宝石可能拿在副手，漏计会导致补给量误判
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.getItem() == Items.EMERALD) count += offhand.getCount();
        return count;
    }

    public void reset() {
        state = State.IDLE;
        opCooldown = 0;
        failStreak = 0;
        readyTimeout = 0;
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