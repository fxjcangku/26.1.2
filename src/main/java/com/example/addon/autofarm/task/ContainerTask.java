package com.example.addon.autofarm.task;

import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.farm.ContainerBroker;
import com.example.addon.farm.FarmPacketOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;

/**
 * 物流任务公共基类：封装「导航 → 开箱 → 同步 → 校验容器 → 转移」的公共流程。
 *
 * 子类只实现具体的 deposit / withdraw 逻辑。所有物流任务都是独占任务，
 * 执行期间 Scanner 只能观察，不能创建新任务打断。
 */
public abstract class ContainerTask implements FarmTask {

    /** 开箱包重试节流间隔（tick） */
    private static final int OPEN_RETRY_INTERVAL = 10;

    protected final BlockPos boxPos;
    protected final ContainerBroker broker;
    protected final double reachDistance;

    private int openRetry;

    protected ContainerTask(BlockPos boxPos, ContainerBroker broker, double reachDistance) {
        this.boxPos = boxPos;
        this.broker = broker;
        this.reachDistance = reachDistance;
    }

    @Override
    public final TaskResult tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return TaskResult.CONTAINER_MISSING;

        // 目标箱必须是容器
        if (!(mc.level.getBlockEntity(boxPos) instanceof Container)) {
            return TaskResult.CONTAINER_MISSING;
        }

        // 导航到箱
        if (!arrived()) {
            if (!FarmNav.available()) return TaskResult.NAVIGATION_FAILED;
            if (!FarmNav.pathing()) FarmNav.goTo(boxPos, 4);
            return TaskResult.IN_PROGRESS;
        }
        FarmNav.cancel();

        // 开箱
        if (ContainerBroker.openMenu() == null) {
            tryOpen();
            return TaskResult.IN_PROGRESS;
        }

        // 等待容器同步稳定
        if (!broker.isReady()) return TaskResult.IN_PROGRESS;

        // 校验打开的是否为目标容器
        if (!ContainerBroker.isBoundContainer(boxPos)) {
            return TaskResult.CONTAINER_OPEN_FAILED;
        }

        return transfer();
    }

    /** 子类实现具体的存入 / 提取逻辑 */
    protected abstract TaskResult transfer();

    @Override
    public boolean exclusive() {
        return true;
    }

    @Override
    public void cancel() {
        FarmNav.cancel();
        ContainerBroker.closeContainer();
        broker.reset();
    }

    private boolean arrived() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isWithinBlockInteractionRange(boxPos, 0.0);
    }

    private void tryOpen() {
        openRetry++;
        if (openRetry < OPEN_RETRY_INTERVAL) return;
        openRetry = 0;
        broker.reset();
        FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, boxPos, Direction.UP);
    }
}
