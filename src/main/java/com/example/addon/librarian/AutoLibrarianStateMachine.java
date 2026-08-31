// 附魔交易所 状态机
package com.example.addon.librarian;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 附魔交易所 · 状态机。
 *
 * <p>驱动 {@link AutoLibrarianState} 状态流转：维护合法转换表、各状态处理器，
 * 通过 {@code transitionListener} 回调每次转换供播报/调试，处理器抛异常时
 * 统一转入 {@link AutoLibrarianState#ERROR} 并通知 {@code errorHandler}。</p>
 */
public final class AutoLibrarianStateMachine {
    /** 状态合法转换表（源状态 → 允许的目标状态集合） */
    private static final Map<AutoLibrarianState, Set<AutoLibrarianState>> ALLOWED_TRANSITIONS = createAllowedTransitions();
    /** 各状态的处理器（IDLE 不接受处理器） */
    private final Map<AutoLibrarianState, Runnable> handlers = new EnumMap<>(AutoLibrarianState.class);
    /** 状态转换监听器（用于播报/调试） */
    private final Consumer<StateTransition> transitionListener;
    /** 错误处理器（处理器抛异常时回调） */
    private final Consumer<RuntimeException> errorHandler;
    /** 当前状态 */
    private AutoLibrarianState currentState = AutoLibrarianState.IDLE;
    /** 累计 tick 计数 */
    private long currentTick;
    /** 进入当前状态的 tick */
    private long stateEnteredTick;

    public AutoLibrarianStateMachine(
        Consumer<StateTransition> transitionListener,
        Consumer<RuntimeException> errorHandler
    ) {
        this.transitionListener = Objects.requireNonNull(transitionListener, "transitionListener");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    /** 注册某状态的处理器（禁止重复注册，IDLE 不接受处理器） */
    public void register(AutoLibrarianState state, Runnable handler) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(handler, "handler");
        if (state == AutoLibrarianState.IDLE) throw new IllegalArgumentException("IDLE 不接受处理器");
        if (handlers.putIfAbsent(state, handler) != null) {
            throw new IllegalArgumentException("状态已注册: " + state);
        }
    }

    /** 启动状态机：进入 START 状态 */
    public void start() {
        transitionTo(AutoLibrarianState.START, "模块启动");
    }

    /** 执行一次合法状态转换（校验转换表，回调监听器） */
    public void transitionTo(AutoLibrarianState nextState, String reason) {
        Objects.requireNonNull(nextState, "nextState");
        if (nextState == currentState) throw new IllegalStateException("不允许重复进入当前状态: " + currentState);
        if (nextState != AutoLibrarianState.IDLE && !ALLOWED_TRANSITIONS.get(currentState).contains(nextState)) {
            throw new IllegalStateException("非法状态转换: " + currentState + " -> " + nextState);
        }
        AutoLibrarianState previousState = currentState;
        currentState = nextState;
        stateEnteredTick = currentTick;
        transitionListener.accept(new StateTransition(previousState, nextState, currentTick, reason));
    }

    /** 推进一 tick：执行当前状态处理器，异常时转入 ERROR */
    public void tick() {
        currentTick++;
        if (currentState == AutoLibrarianState.IDLE) return;
        Runnable handler = handlers.get(currentState);
        if (handler == null) {
            fail(new IllegalStateException("状态没有处理器: " + currentState));
            return;
        }
        try {
            handler.run();
        } catch (RuntimeException exception) {
            fail(exception);
        }
    }

    /** 重置状态机到 IDLE */
    public void reset(String reason) {
        transitionTo(AutoLibrarianState.IDLE, reason);
    }

    /** 处理器抛异常时转入 ERROR 并通知错误处理器 */
    private void fail(RuntimeException exception) {
        if (currentState != AutoLibrarianState.ERROR) {
            transitionTo(AutoLibrarianState.ERROR, exception.getMessage());
        }
        errorHandler.accept(exception);
    }

    /** 构建状态合法转换表：默认任意状态均可转 ERROR，再逐条声明业务转换 */
    private static Map<AutoLibrarianState, Set<AutoLibrarianState>> createAllowedTransitions() {
        Map<AutoLibrarianState, Set<AutoLibrarianState>> transitions = new EnumMap<>(AutoLibrarianState.class);
        for (AutoLibrarianState state : AutoLibrarianState.values()) {
            transitions.put(state, EnumSet.of(AutoLibrarianState.ERROR));
        }
        transitions.put(AutoLibrarianState.IDLE, EnumSet.of(AutoLibrarianState.START));
        allow(transitions, AutoLibrarianState.START, AutoLibrarianState.SEARCH_VILLAGER, AutoLibrarianState.FINISH);
        allow(transitions, AutoLibrarianState.SEARCH_VILLAGER, AutoLibrarianState.SELECT_TARGET_VILLAGER);
        allow(transitions, AutoLibrarianState.SELECT_TARGET_VILLAGER, AutoLibrarianState.MOVE_TO_VILLAGER, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.MOVE_TO_VILLAGER, AutoLibrarianState.FIND_LECTERN_POSITION, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.FIND_LECTERN_POSITION, AutoLibrarianState.MOVE_TO_STAND_POSITION, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.MOVE_TO_STAND_POSITION, AutoLibrarianState.BREAK_OBSTACLE, AutoLibrarianState.PLACE_LECTERN, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.BREAK_OBSTACLE, AutoLibrarianState.PLACE_LECTERN, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.PLACE_LECTERN, AutoLibrarianState.BREAK_OBSTACLE, AutoLibrarianState.WAIT_PROFESSION, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.WAIT_PROFESSION, AutoLibrarianState.OPEN_TRADE, AutoLibrarianState.RESET, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.OPEN_TRADE, AutoLibrarianState.WAIT_TRADE_SCREEN, AutoLibrarianState.RESET, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.WAIT_TRADE_SCREEN, AutoLibrarianState.READ_TRADES, AutoLibrarianState.RESET, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.READ_TRADES, AutoLibrarianState.CHECK_ENCHANTMENT, AutoLibrarianState.RESET, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.CHECK_ENCHANTMENT, AutoLibrarianState.RESET, AutoLibrarianState.SUCCESS_FOUND, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.RESET, AutoLibrarianState.BREAK_LECTERN, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.BREAK_LECTERN, AutoLibrarianState.WAIT_UNEMPLOYED, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.WAIT_UNEMPLOYED, AutoLibrarianState.FIND_LECTERN_POSITION, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.SUCCESS_FOUND, AutoLibrarianState.TRADE_PROCESS, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.TRADE_PROCESS, AutoLibrarianState.SELECT_TRADE, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.SELECT_TRADE, AutoLibrarianState.WAIT_TRADE_SYNC, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.WAIT_TRADE_SYNC, AutoLibrarianState.TAKE_TRADE_OUTPUT, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.TAKE_TRADE_OUTPUT, AutoLibrarianState.VERIFY_PURCHASE, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.VERIFY_PURCHASE, AutoLibrarianState.COMPLETE_TARGET, AutoLibrarianState.END_VILLAGER_CYCLE);
        allow(transitions, AutoLibrarianState.COMPLETE_TARGET, AutoLibrarianState.END_VILLAGER_CYCLE, AutoLibrarianState.FINISH);
        allow(transitions, AutoLibrarianState.END_VILLAGER_CYCLE, AutoLibrarianState.SEARCH_VILLAGER);
        return Map.copyOf(transitions);
    }

    /** 向转换表添加「源状态 → 多个目标状态」的合法转换 */
    private static void allow(
        Map<AutoLibrarianState, Set<AutoLibrarianState>> transitions,
        AutoLibrarianState source,
        AutoLibrarianState... targets
    ) {
        transitions.get(source).addAll(EnumSet.of(targets[0], targets));
    }

    /** 返回当前状态 */
    public AutoLibrarianState getCurrentState() {
        return currentState;
    }

    /** 返回累计 tick 计数 */
    public long getCurrentTick() {
        return currentTick;
    }

    /** 返回停留在当前状态的 tick 数 */
    public long getTicksInCurrentState() {
        return currentTick - stateEnteredTick;
    }
}
