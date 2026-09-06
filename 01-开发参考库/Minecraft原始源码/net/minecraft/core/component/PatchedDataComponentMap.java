package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class PatchedDataComponentMap implements DataComponentMap {
	private final DataComponentMap prototype;
	private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;
	private boolean copyOnWrite;

	public PatchedDataComponentMap(final DataComponentMap prototype) {
		this(prototype, Reference2ObjectMaps.emptyMap(), true);
	}

	private PatchedDataComponentMap(
		final DataComponentMap prototype, final Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch, final boolean copyOnWrite
	) {
		this.prototype = prototype;
		this.patch = patch;
		this.copyOnWrite = copyOnWrite;
	}

	public static PatchedDataComponentMap fromPatch(final DataComponentMap prototype, final DataComponentPatch patch) {
		if (isPatchSanitized(prototype, patch.map)) {
			return new PatchedDataComponentMap(prototype, patch.map, true);
		} else {
			PatchedDataComponentMap map = new PatchedDataComponentMap(prototype);
			map.applyPatch(patch);
			return map;
		}
	}

	private static boolean isPatchSanitized(final DataComponentMap prototype, final Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch) {
		for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(patch)) {
			Object defaultValue = prototype.get((DataComponentType)entry.getKey());
			Optional<?> value = (Optional<?>)entry.getValue();
			if (value.isPresent() && value.get().equals(defaultValue)) {
				return false;
			}

			if (value.isEmpty() && defaultValue == null) {
				return false;
			}
		}

		return true;
	}

	@Nullable
	@Override
	public <T> T get(final DataComponentType<? extends T> type) {
		return DataComponentPatch.getFromPatchAndPrototype(this.patch, this.prototype, type);
	}

	public boolean hasNonDefault(final DataComponentType<?> type) {
		return this.patch.containsKey(type);
	}

	@Nullable
	public <T> T set(final DataComponentType<T> type, @Nullable final T value) {
		this.ensureMapOwnership();
		T defaultValue = this.prototype.get(type);
		Optional<T> lastValue;
		if (Objects.equals(value, defaultValue)) {
			lastValue = (Optional<T>)this.patch.remove(type);
		} else {
			lastValue = (Optional<T>)this.patch.put(type, Optional.ofNullable(value));
		}

		return (T)(lastValue != null ? lastValue.orElse(defaultValue) : defaultValue);
	}

	@Nullable
	public <T> T set(final TypedDataComponent<T> value) {
		return this.set(value.type(), value.value());
	}

	@Nullable
	public <T> T remove(final DataComponentType<? extends T> type) {
		this.ensureMapOwnership();
		T defaultValue = this.prototype.get(type);
		Optional<? extends T> lastValue;
		if (defaultValue != null) {
			lastValue = (Optional<? extends T>)this.patch.put(type, Optional.empty());
		} else {
			lastValue = (Optional<? extends T>)this.patch.remove(type);
		}

		return (T)(lastValue != null ? lastValue.orElse(null) : defaultValue);
	}

	public void applyPatch(final DataComponentPatch patch) {
		this.ensureMapOwnership();

		for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(patch.map)) {
			this.applyPatch((DataComponentType<?>)entry.getKey(), (Optional<?>)entry.getValue());
		}
	}

	private void applyPatch(final DataComponentType<?> type, final Optional<?> value) {
		Object defaultValue = this.prototype.get(type);
		if (value.isPresent()) {
			if (value.get().equals(defaultValue)) {
				this.patch.remove(type);
			} else {
				this.patch.put(type, value);
			}
		} else if (defaultValue != null) {
			this.patch.put(type, Optional.empty());
		} else {
			this.patch.remove(type);
		}
	}

	public void restorePatch(final DataComponentPatch patch) {
		this.ensureMapOwnership();
		this.patch.clear();
		this.patch.putAll(patch.map);
	}

	public void clearPatch() {
		this.ensureMapOwnership();
		this.patch.clear();
	}

	public void setAll(final DataComponentMap components) {
		for (TypedDataComponent<?> entry : components) {
			entry.applyTo(this);
		}
	}

	private void ensureMapOwnership() {
		if (this.copyOnWrite) {
			this.patch = new Reference2ObjectArrayMap<>(this.patch);
			this.copyOnWrite = false;
		}
	}

	@Override
	public Set<DataComponentType<?>> keySet() {
		if (this.patch.isEmpty()) {
			return this.prototype.keySet();
		} else {
			Set<DataComponentType<?>> components = new ReferenceArraySet<>(this.prototype.keySet());

			for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
				Optional<?> value = (Optional<?>)entry.getValue();
				if (value.isPresent()) {
					components.add((DataComponentType)entry.getKey());
				} else {
					components.remove(entry.getKey());
				}
			}

			return components;
		}
	}

	@Override
	public Iterator<TypedDataComponent<?>> iterator() {
		if (this.patch.isEmpty()) {
			return this.prototype.iterator();
		} else {
			List<TypedDataComponent<?>> components = new ArrayList(this.patch.size() + this.prototype.size());

			for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
				if (((Optional)entry.getValue()).isPresent()) {
					components.add(TypedDataComponent.createUnchecked((DataComponentType)entry.getKey(), ((Optional)entry.getValue()).get()));
				}
			}

			for (TypedDataComponent<?> component : this.prototype) {
				if (!this.patch.containsKey(component.type())) {
					components.add(component);
				}
			}

			return components.iterator();
		}
	}

	@Override
	public int size() {
		int size = this.prototype.size();

		for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
			boolean inPatch = ((Optional)entry.getValue()).isPresent();
			boolean inPrototype = this.prototype.has((DataComponentType<?>)entry.getKey());
			if (inPatch != inPrototype) {
				size += inPatch ? 1 : -1;
			}
		}

		return size;
	}

	public DataComponentPatch asPatch() {
		if (this.patch.isEmpty()) {
			return DataComponentPatch.EMPTY;
		} else {
			this.copyOnWrite = true;
			return new DataComponentPatch(this.patch);
		}
	}

	public PatchedDataComponentMap copy() {
		this.copyOnWrite = true;
		return new PatchedDataComponentMap(this.prototype, this.patch, true);
	}

	public DataComponentMap toImmutableMap() {
		return (DataComponentMap)(this.patch.isEmpty() ? this.prototype : this.copy());
	}

	public boolean equals(final Object obj) {
		return this == obj ? true : obj instanceof PatchedDataComponentMap otherMap && this.prototype.equals(otherMap.prototype) && this.patch.equals(otherMap.patch);
	}

	public int hashCode() {
		return this.prototype.hashCode() + this.patch.hashCode() * 31;
	}

	public String toString() {
		return "{" + (String)this.stream().map(TypedDataComponent::toString).collect(Collectors.joining(", ")) + "}";
	}
}
