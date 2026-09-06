package net.minecraft.util;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

public final class SequencedPriorityIterator<T> extends AbstractIterator<T> {
	private static final int MIN_PRIO = Integer.MIN_VALUE;
	@Nullable
	private Deque<T> highestPrioQueue = null;
	private int highestPrio = Integer.MIN_VALUE;
	private final Int2ObjectMap<Deque<T>> queuesByPriority = new Int2ObjectOpenHashMap<>();

	public void add(final T data, final int priority) {
		if (priority == this.highestPrio && this.highestPrioQueue != null) {
			this.highestPrioQueue.addLast(data);
		} else {
			Deque<T> queue = this.queuesByPriority.computeIfAbsent(priority, (Int2ObjectFunction<? extends Deque<T>>)(order -> Queues.<T>newArrayDeque()));
			queue.addLast(data);
			if (priority >= this.highestPrio) {
				this.highestPrioQueue = queue;
				this.highestPrio = priority;
			}
		}
	}

	@Nullable
	@Override
	protected T computeNext() {
		if (this.highestPrioQueue == null) {
			return this.endOfData();
		} else {
			T result = (T)this.highestPrioQueue.removeFirst();
			if (result == null) {
				return this.endOfData();
			} else {
				if (this.highestPrioQueue.isEmpty()) {
					this.switchCacheToNextHighestPrioQueue();
				}

				return result;
			}
		}
	}

	private void switchCacheToNextHighestPrioQueue() {
		int foundHighestPrio = Integer.MIN_VALUE;
		Deque<T> foundHighestPrioQueue = null;

		for (Entry<Deque<T>> entry : Int2ObjectMaps.fastIterable(this.queuesByPriority)) {
			Deque<T> queue = (Deque<T>)entry.getValue();
			int prio = entry.getIntKey();
			if (prio > foundHighestPrio && !queue.isEmpty()) {
				foundHighestPrio = prio;
				foundHighestPrioQueue = queue;
				if (prio == this.highestPrio - 1) {
					break;
				}
			}
		}

		this.highestPrio = foundHighestPrio;
		this.highestPrioQueue = foundHighestPrioQueue;
	}
}
