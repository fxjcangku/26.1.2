package net.minecraft.util;

import java.io.Serializable;
import java.util.Deque;
import java.util.List;
import java.util.RandomAccess;
import org.jspecify.annotations.Nullable;

public interface ListAndDeque<T> extends List<T>, RandomAccess, Cloneable, Serializable, Deque<T> {
	ListAndDeque<T> reversed();

	T getFirst();

	T getLast();

	void addFirst(T t);

	void addLast(T t);

	T removeFirst();

	T removeLast();

	default boolean offer(final T value) {
		return this.offerLast(value);
	}

	default T remove() {
		return this.removeFirst();
	}

	@Nullable
	default T poll() {
		return (T)this.pollFirst();
	}

	default T element() {
		return this.getFirst();
	}

	@Nullable
	default T peek() {
		return (T)this.peekFirst();
	}

	default void push(final T value) {
		this.addFirst(value);
	}

	default T pop() {
		return this.removeFirst();
	}
}
