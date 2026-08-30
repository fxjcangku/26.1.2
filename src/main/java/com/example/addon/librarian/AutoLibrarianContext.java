// 附魔交易所 运行上下文
package com.example.addon.librarian;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.InventorySnapshot;
import com.example.addon.librarian.model.TargetProgress;
import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.model.TradeVerificationContext;
import com.example.addon.librarian.model.VillagerTarget;
import com.example.addon.librarian.model.VillagerStation;

import java.util.Objects;
import java.util.Optional;

public final class AutoLibrarianContext {
    private final GlobalTaskLifecycle globalTask;
    private final VillagerCycleLifecycle villagerCycle = new VillagerCycleLifecycle();
    private final RefreshAttemptLifecycle refreshAttempt = new RefreshAttemptLifecycle();
    private TradeVerificationContext tradeVerification;
    private long inventorySampleSequence;

    public AutoLibrarianContext(TargetProgress targetProgress) {
        globalTask = new GlobalTaskLifecycle(targetProgress);
    }

    public void resetRefreshAttempt() {
        refreshAttempt.clear();
        tradeVerification = null;
    }

    public void completeLecternRemoval() {
        villagerCycle.clearLectern();
    }

    public void beginTradeProcess() {
        if (villagerCycle.villagerTarget == null
            || villagerCycle.lecternPosition == null
            || refreshAttempt.tradeOffer == null
            || refreshAttempt.matchedTarget == null) {
            throw new IllegalStateException("目标交易上下文不完整");
        }
        if (tradeVerification != null) throw new IllegalStateException("当前交易验证尚未结束");
        tradeVerification = new TradeVerificationContext(refreshAttempt.matchedTarget, refreshAttempt.tradeOffer);
        refreshAttempt.clear();
    }

    public void completeCurrentTarget() {
        completeVerifiedCurrentTarget();
    }

    public void completeVerifiedCurrentTarget() {
        completeVerifiedCurrentTarget(false);
    }

    public void completeVerifiedCurrentTarget(boolean removeCompletedTarget) {
        TradeVerificationContext verification = requireTradeVerification();
        if (verification.verificationStatus() != TradeVerificationContext.VerificationStatus.SUCCEEDED) {
            throw new IllegalStateException("当前目标交易尚未通过库存验证");
        }
        globalTask.targetProgress.complete(verification.target(), removeCompletedTarget);
        tradeVerification = null;
    }

    public void clearVillagerCycle() {
        villagerCycle.clear();
        resetRefreshAttempt();
    }

    public TargetProgress targetProgress() {
        return globalTask.targetProgress;
    }

    public Optional<VillagerTarget> villagerTarget() {
        return Optional.ofNullable(villagerCycle.villagerTarget);
    }

    public void setVillagerTarget(VillagerTarget villagerTarget) {
        villagerCycle.begin(villagerTarget);
    }

    public Optional<BlockPosition> lecternPosition() {
        return Optional.ofNullable(villagerCycle.lecternPosition);
    }

    public void setLecternPosition(BlockPosition lecternPosition) {
        villagerCycle.lecternPosition = Objects.requireNonNull(lecternPosition, "lecternPosition");
    }

    public Optional<VillagerStation> villagerStation() {
        return Optional.ofNullable(villagerCycle.villagerStation);
    }

    public void setVillagerStation(VillagerStation villagerStation) {
        VillagerStation station = Objects.requireNonNull(villagerStation, "villagerStation");
        if (villagerCycle.villagerTarget == null) throw new IllegalStateException("当前村民周期尚未开始");
        if (!villagerCycle.villagerTarget.uuid().equals(station.villagerUuid())) {
            throw new IllegalArgumentException("交易位必须属于当前村民");
        }
        villagerCycle.villagerStation = station;
        villagerCycle.lecternPosition = station.lecternPosition();
    }

    public Optional<TradeOfferSnapshot> tradeOffer() {
        return tradeVerification == null
            ? Optional.ofNullable(refreshAttempt.tradeOffer)
            : Optional.of(tradeVerification.tradeOffer());
    }

    public void setTradeOffer(TradeOfferSnapshot tradeOffer) {
        if (tradeVerification != null) throw new IllegalStateException("交易验证期间不能覆盖当前报价");
        refreshAttempt.tradeOffer = Objects.requireNonNull(tradeOffer, "tradeOffer");
    }

    public Optional<EnchantmentTarget> matchedTarget() {
        return tradeVerification == null
            ? Optional.ofNullable(refreshAttempt.matchedTarget)
            : Optional.of(tradeVerification.target());
    }

    public void setMatchedTarget(EnchantmentTarget matchedTarget) {
        if (tradeVerification != null) throw new IllegalStateException("交易验证期间不能覆盖当前目标");
        refreshAttempt.matchedTarget = Objects.requireNonNull(matchedTarget, "matchedTarget");
    }

    public int matchingBooksBeforePurchase() {
        return tradeVerification == null
            ? 0
            : tradeVerification.beforePurchase().map(InventorySnapshot::matchingBookCount).orElse(0);
    }

    public void setMatchingBooksBeforePurchase(int count) {
        if (count < 0) throw new IllegalArgumentException("库存数量不能小于 0");
        TradeVerificationContext verification = requireTradeVerification();
        verification.captureBeforePurchase(snapshot(verification.target(), count));
    }

    public boolean verifyCurrentPurchase(int matchingBookCount) {
        if (matchingBookCount < 0) throw new IllegalArgumentException("库存数量不能小于 0");
        TradeVerificationContext verification = requireTradeVerification();
        if (verification.beforePurchase().isEmpty()) throw new IllegalStateException("缺少购买前库存快照");
        if (verification.requestStatus() == TradeVerificationContext.RequestStatus.NOT_SUBMITTED) {
            verification.markRequestSubmitted();
            verification.markRequestAccepted();
        }
        if (verification.synchronizationStatus() == TradeVerificationContext.SynchronizationStatus.NOT_WAITING) {
            verification.beginSynchronizationWait();
        }
        verification.recordSynchronizedSnapshot(snapshot(verification.target(), matchingBookCount));
        return verification.verifyPurchase();
    }

    public Optional<TradeVerificationContext> tradeVerification() {
        return Optional.ofNullable(tradeVerification);
    }

    public boolean tradeProcessActive() {
        return tradeVerification != null;
    }

    public boolean hasPendingLectern() {
        return villagerCycle.lecternPosition != null;
    }

    public String lastFailureReason() {
        return globalTask.lastFailureReason;
    }

    public void setLastFailureReason(String reason) {
        globalTask.lastFailureReason = Objects.requireNonNullElse(reason, "");
    }

    private InventorySnapshot snapshot(EnchantmentTarget target, int matchingBookCount) {
        return new InventorySnapshot(target.identifier(), target.level(), matchingBookCount, inventorySampleSequence++);
    }

    private TradeVerificationContext requireTradeVerification() {
        if (tradeVerification == null) throw new IllegalStateException("当前没有可用的交易验证上下文");
        return tradeVerification;
    }

    private static final class GlobalTaskLifecycle {
        private final TargetProgress targetProgress;
        private String lastFailureReason = "";

        private GlobalTaskLifecycle(TargetProgress targetProgress) {
            this.targetProgress = Objects.requireNonNull(targetProgress, "targetProgress");
        }
    }

    private static final class VillagerCycleLifecycle {
        private VillagerTarget villagerTarget;
        private BlockPosition lecternPosition;
        private VillagerStation villagerStation;

        private void begin(VillagerTarget target) {
            if (villagerTarget != null) throw new IllegalStateException("当前村民周期尚未结束");
            villagerTarget = Objects.requireNonNull(target, "villagerTarget");
        }

        private void clearLectern() {
            lecternPosition = villagerStation == null ? null : villagerStation.lecternPosition();
        }

        private void clear() {
            villagerTarget = null;
            lecternPosition = null;
            villagerStation = null;
        }
    }

    private static final class RefreshAttemptLifecycle {
        private TradeOfferSnapshot tradeOffer;
        private EnchantmentTarget matchedTarget;

        private void clear() {
            tradeOffer = null;
            matchedTarget = null;
        }
    }
}
