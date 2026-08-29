package com.example.addon.villager.logistics;

import com.example.addon.villager.data.VillagerTradeTarget;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline 任务定义
 *
 * 代表流水线中的单个任务：
 * · 目标职业
 * · 目标物品列表
 * · 本任务的价格上限与购买总量（每任务独立，互不影响）
 * · 任务完成状态
 */
public class PipelineTask {

    private final VillagerProfession profession;
    private final List<VillagerTradeTarget> targets;
    private final int maxPrice;
    private final int targetQuantity;
    private boolean completed;

    public PipelineTask(VillagerProfession profession, List<VillagerTradeTarget> targets) {
        this(profession, targets, 64, 64);
    }

    public PipelineTask(VillagerProfession profession, List<VillagerTradeTarget> targets,
                        int maxPrice, int targetQuantity) {
        this.profession = profession;
        this.targets = new ArrayList<>(targets);
        this.maxPrice = Math.max(1, maxPrice);
        this.targetQuantity = Math.max(1, targetQuantity);
        this.completed = false;
    }

    public VillagerProfession getProfession() {
        return profession;
    }

    public List<VillagerTradeTarget> getTargets() {
        return targets;
    }

    public int getMaxPrice() {
        return maxPrice;
    }

    public int getTargetQuantity() {
        return targetQuantity;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public String toString() {
        return String.format("Task[%s, %d targets, price<=%d, qty=%d, %s]",
            profession, targets.size(), maxPrice, targetQuantity, completed ? "完成" : "进行中");
    }
}