package net.minecraft.core;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class NonNullList<E> extends AbstractList<E> {
	private final List<E> list;
	@Nullable
	private final E defaultValue;

	public static <E> NonNullList<E> create() {
		return new NonNullList<>(Lists.<E>newArrayList(), null);
	}

	public static <E> NonNullList<E> createWithCapacity(final int capacity) {
		return new NonNullList<>(Lists.<E>newArrayListWithCapacity(capacity), null);
	}

	public static <E> NonNullList<E> withSize(final int size, final E defaultValue) {
		Objects.requireNonNull(defaultValue);
		Object[] objects = new Object[size];
		Arrays.fill(objects, defaultValue);
		return new NonNullList<>(Arrays.asList(objects), defaultValue);
	}

	@SafeVarargs
	public static <E> NonNullList<E> of(final E defaultValue, final E... values) {
		return new NonNullList<>(Arrays.asList(values), defaultValue);
	}

	protected NonNullList(final List<E> list, @Nullable final E defaultValue) {
		this.list = list;
		this.defaultValue = defaultValue;
	}

	public E get(final int index) {
		return (E)this.list.get(index);
	}

	public E set(final int index, final E element) {
		Objects.requireNonNull(element);
		return (E)this.list.set(index, element);
	}

	public void add(final int index, final E element) {
		Objects.requireNonNull(element);
		this.list.add(index, element);
	}

	public E remove(final int index) {
		return (E)this.list.remove(index);
	}

	public int size() {
		return this.list.size();
	}

	public void clear() {
		if (this.defaultValue == null) {
			super.clear();
		} else {
			for (int i = 0; i < this.size(); i++) {
				this.set(i, this.defaultValue);
			}
		}
	}
}
