// 附魔交易所 附魔目标进度
package com.example.addon.librarian.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TargetProgress {
    private final List<EnchantmentTarget> targets;
    private final int configuredTargetCount;

    public TargetProgress(List<EnchantmentTarget> configuredTargets) {
        List<EnchantmentTarget> copiedTargets = List.copyOf(Objects.requireNonNull(configuredTargets, "configuredTargets"));
        if (copiedTargets.isEmpty()) throw new IllegalArgumentException("至少需要一个目标附魔");
        targets = new ArrayList<>(copiedTargets);
        configuredTargetCount = copiedTargets.size();
    }

    public Optional<EnchantmentTarget> currentTarget() {
        return targets.stream()
            .filter(target -> !target.completed())
            .findFirst();
    }

    public List<EnchantmentTarget> incompleteTargets() {
        return targets.stream()
            .filter(target -> !target.completed())
            .toList();
    }

    public void complete(EnchantmentTarget target) {
        complete(target, false);
    }

    public void complete(EnchantmentTarget target, boolean removeCompletedTarget) {
        Objects.requireNonNull(target, "target");
        int index = findTargetIndex(target);
        if (removeCompletedTarget) {
            targets.remove(index);
        } else {
            targets.set(index, targets.get(index).asCompleted());
        }
    }

    public boolean isCompleted(EnchantmentTarget target) {
        return targets.get(findTargetIndex(target)).completed();
    }

    public boolean allCompleted() {
        return currentTarget().isEmpty();
    }

    public int completedCount() {
        int removedCount = configuredTargetCount - targets.size();
        return removedCount + (int) targets.stream().filter(EnchantmentTarget::completed).count();
    }

    private int findTargetIndex(EnchantmentTarget target) {
        for (int i = 0; i < targets.size(); i++) {
            EnchantmentTarget configuredTarget = targets.get(i);
            if (configuredTarget.identifier().equals(target.identifier()) && configuredTarget.level() == target.level()) {
                return i;
            }
        }
        throw new IllegalArgumentException("目标不在配置列表中");
    }
}
