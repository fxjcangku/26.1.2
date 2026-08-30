// 附魔交易所 单次交易验证上下文
package com.example.addon.librarian.model;

import java.util.Objects;
import java.util.Optional;

public final class TradeVerificationContext {
    public enum RequestStatus {
        NOT_SUBMITTED,
        SUBMITTED,
        ACCEPTED,
        FAILED
    }

    public enum SynchronizationStatus {
        NOT_WAITING,
        WAITING,
        SYNCHRONIZED,
        TIMED_OUT
    }

    public enum VerificationStatus {
        NOT_VERIFIED,
        SUCCEEDED,
        FAILED
    }

    private final EnchantmentTarget target;
    private final TradeOfferSnapshot tradeOffer;
    private InventorySnapshot beforePurchase;
    private InventorySnapshot afterSynchronization;
    private RequestStatus requestStatus = RequestStatus.NOT_SUBMITTED;
    private SynchronizationStatus synchronizationStatus = SynchronizationStatus.NOT_WAITING;
    private VerificationStatus verificationStatus = VerificationStatus.NOT_VERIFIED;
    private String failureReason = "";

    public TradeVerificationContext(EnchantmentTarget target, TradeOfferSnapshot tradeOffer) {
        this.target = Objects.requireNonNull(target, "target");
        this.tradeOffer = Objects.requireNonNull(tradeOffer, "tradeOffer");
        if (!target.identifier().equals(tradeOffer.enchantmentIdentifier())
            || target.level() != tradeOffer.enchantmentLevel()) {
            throw new IllegalArgumentException("锁定报价必须匹配当前目标附魔 ID 和准确等级");
        }
    }

    public void captureBeforePurchase(InventorySnapshot snapshot) {
        requireMatchingSnapshot(snapshot);
        if (beforePurchase != null) throw new IllegalStateException("购买前库存快照已经记录");
        beforePurchase = snapshot;
    }

    public void markRequestSubmitted() {
        requireRequestStatus(RequestStatus.NOT_SUBMITTED);
        requestStatus = RequestStatus.SUBMITTED;
    }

    public void markRequestAccepted() {
        requireRequestStatus(RequestStatus.SUBMITTED);
        requestStatus = RequestStatus.ACCEPTED;
    }

    public void markRequestFailed(String reason) {
        if (requestStatus == RequestStatus.ACCEPTED) throw new IllegalStateException("已接受请求不能标记为提交失败");
        requestStatus = RequestStatus.FAILED;
        fail(reason);
    }

    public void beginSynchronizationWait() {
        if (requestStatus != RequestStatus.ACCEPTED) throw new IllegalStateException("交易请求尚未被接受");
        if (beforePurchase == null) throw new IllegalStateException("缺少购买前库存快照");
        if (synchronizationStatus != SynchronizationStatus.NOT_WAITING) {
            throw new IllegalStateException("服务器同步等待已经开始");
        }
        synchronizationStatus = SynchronizationStatus.WAITING;
    }

    public void recordSynchronizedSnapshot(InventorySnapshot snapshot) {
        if (synchronizationStatus != SynchronizationStatus.WAITING) {
            throw new IllegalStateException("当前未等待服务器同步");
        }
        requireMatchingSnapshot(snapshot);
        afterSynchronization = snapshot;
        synchronizationStatus = SynchronizationStatus.SYNCHRONIZED;
    }

    public void markSynchronizationTimedOut(String reason) {
        if (synchronizationStatus != SynchronizationStatus.WAITING) {
            throw new IllegalStateException("当前未等待服务器同步");
        }
        synchronizationStatus = SynchronizationStatus.TIMED_OUT;
        fail(reason);
    }

    public boolean verifyPurchase() {
        if (synchronizationStatus != SynchronizationStatus.SYNCHRONIZED
            || beforePurchase == null
            || afterSynchronization == null) {
            return false;
        }
        if (afterSynchronization.hasIncreaseFrom(beforePurchase)) {
            verificationStatus = VerificationStatus.SUCCEEDED;
            failureReason = "";
            return true;
        }
        fail("服务器同步后目标附魔书库存数量未增加");
        return false;
    }

    public void fail(String reason) {
        verificationStatus = VerificationStatus.FAILED;
        failureReason = Objects.requireNonNullElse(reason, "");
    }

    public EnchantmentTarget target() {
        return target;
    }

    public TradeOfferSnapshot tradeOffer() {
        return tradeOffer;
    }

    public Optional<InventorySnapshot> beforePurchase() {
        return Optional.ofNullable(beforePurchase);
    }

    public Optional<InventorySnapshot> afterSynchronization() {
        return Optional.ofNullable(afterSynchronization);
    }

    public RequestStatus requestStatus() {
        return requestStatus;
    }

    public SynchronizationStatus synchronizationStatus() {
        return synchronizationStatus;
    }

    public VerificationStatus verificationStatus() {
        return verificationStatus;
    }

    public String failureReason() {
        return failureReason;
    }

    private void requireMatchingSnapshot(InventorySnapshot snapshot) {
        if (!Objects.requireNonNull(snapshot, "snapshot").matchesTarget(target)) {
            throw new IllegalArgumentException("库存快照必须匹配当前目标附魔 ID 和准确等级");
        }
    }

    private void requireRequestStatus(RequestStatus expected) {
        if (requestStatus != expected) throw new IllegalStateException("交易请求状态不允许当前操作");
    }
}
