package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MappedRegistry<T> implements WritableRegistry<T> {
	private final ResourceKey<? extends Registry<T>> key;
	private final ObjectList<Holder.Reference<T>> byId = new ObjectArrayList<>(256);
	private final Reference2IntMap<T> toId = Util.make(new Reference2IntOpenHashMap<>(), t -> t.defaultReturnValue(-1));
	private final Map<Identifier, Holder.Reference<T>> byLocation = new HashMap();
	private final Map<ResourceKey<T>, Holder.Reference<T>> byKey = new HashMap();
	private final Map<T, Holder.Reference<T>> byValue = new IdentityHashMap();
	private final Map<ResourceKey<T>, RegistrationInfo> registrationInfos = new IdentityHashMap();
	private Lifecycle registryLifecycle;
	private final Map<TagKey<T>, HolderSet.Named<T>> frozenTags = new IdentityHashMap();
	private MappedRegistry.TagSet<T> allTags = MappedRegistry.TagSet.unbound();
	@Nullable
	private DataComponentLookup<T> componentLookup;
	private boolean frozen;
	@Nullable
	private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

	@Override
	public Stream<HolderSet.Named<T>> listTags() {
		return this.getTags();
	}

	public MappedRegistry(final ResourceKey<? extends Registry<T>> key, final Lifecycle lifecycle) {
		this(key, lifecycle, false);
	}

	public MappedRegistry(final ResourceKey<? extends Registry<T>> key, final Lifecycle initialLifecycle, final boolean intrusiveHolders) {
		this.key = key;
		this.registryLifecycle = initialLifecycle;
		if (intrusiveHolders) {
			this.unregisteredIntrusiveHolders = new IdentityHashMap();
		}
	}

	@Override
	public ResourceKey<? extends Registry<T>> key() {
		return this.key;
	}

	public String toString() {
		return "Registry[" + this.key + " (" + this.registryLifecycle + ")]";
	}

	private void validateWrite() {
		if (this.frozen) {
			throw new IllegalStateException("Registry is already frozen");
		}
	}

	private void validateWrite(final ResourceKey<T> key) {
		if (this.frozen) {
			throw new IllegalStateException("Registry is already frozen (trying to add key " + key + ")");
		}
	}

	@Override
	public Holder.Reference<T> register(final ResourceKey<T> key, final T value, final RegistrationInfo registrationInfo) {
		this.validateWrite(key);
		Objects.requireNonNull(key);
		Objects.requireNonNull(value);
		if (this.byLocation.containsKey(key.identifier())) {
			throw (IllegalStateException)Util.pauseInIde((T)(new IllegalStateException("Adding duplicate key '" + key + "' to registry")));
		} else if (this.byValue.containsKey(value)) {
			throw (IllegalStateException)Util.pauseInIde((T)(new IllegalStateException("Adding duplicate value '" + value + "' to registry")));
		} else {
			Holder.Reference<T> holder;
			if (this.unregisteredIntrusiveHolders != null) {
				holder = (Holder.Reference<T>)this.unregisteredIntrusiveHolders.remove(value);
				if (holder == null) {
					throw new AssertionError("Missing intrusive holder for " + key + ":" + value);
				}

				holder.bindKey(key);
			} else {
				holder = (Holder.Reference<T>)this.byKey.computeIfAbsent(key, k -> Holder.Reference.createStandAlone(this, k));
			}

			this.byKey.put(key, holder);
			this.byLocation.put(key.identifier(), holder);
			this.byValue.put(value, holder);
			int newId = this.byId.size();
			this.byId.add(holder);
			this.toId.put(value, newId);
			this.registrationInfos.put(key, registrationInfo);
			this.registryLifecycle = this.registryLifecycle.add(registrationInfo.lifecycle());
			return holder;
		}
	}

	@Nullable
	@Override
	public Identifier getKey(final T thing) {
		Holder.Reference<T> holder = (Holder.Reference<T>)this.byValue.get(thing);
		return holder != null ? holder.key().identifier() : null;
	}

	@Override
	public Optional<ResourceKey<T>> getResourceKey(final T thing) {
		return Optional.ofNullable((Holder.Reference)this.byValue.get(thing)).map(Holder.Reference::key);
	}

	@Override
	public int getId(@Nullable final T thing) {
		return this.toId.getInt(thing);
	}

	@Nullable
	@Override
	public T getValue(@Nullable final ResourceKey<T> key) {
		return getValueFromNullable((Holder.Reference<T>)this.byKey.get(key));
	}

	@Nullable
	@Override
	public T byId(final int id) {
		return (T)(id >= 0 && id < this.byId.size() ? ((Holder.Reference)this.byId.get(id)).value() : null);
	}

	@Override
	public Optional<Holder.Reference<T>> get(final int id) {
		return id >= 0 && id < this.byId.size() ? Optional.ofNullable((Holder.Reference)this.byId.get(id)) : Optional.empty();
	}

	@Override
	public Optional<Holder.Reference<T>> get(final Identifier id) {
		return Optional.ofNullable((Holder.Reference)this.byLocation.get(id));
	}

	@Override
	public Optional<Holder.Reference<T>> get(final ResourceKey<T> id) {
		return Optional.ofNullable((Holder.Reference)this.byKey.get(id));
	}

	@Override
	public Optional<Holder.Reference<T>> getAny() {
		return this.byId.isEmpty() ? Optional.empty() : Optional.of((Holder.Reference)this.byId.getFirst());
	}

	@Override
	public Holder<T> wrapAsHolder(final T value) {
		Holder.Reference<T> existingHolder = (Holder.Reference<T>)this.byValue.get(value);
		return (Holder<T>)(existingHolder != null ? existingHolder : Holder.direct(value));
	}

	private Holder.Reference<T> getOrCreateHolderOrThrow(final ResourceKey<T> key) {
		return (Holder.Reference<T>)this.byKey.computeIfAbsent(key, id -> {
			if (this.unregisteredIntrusiveHolders != null) {
				throw new IllegalStateException("This registry can't create new holders without value");
			} else {
				this.validateWrite(id);
				return Holder.Reference.createStandAlone(this, id);
			}
		});
	}

	@Override
	public int size() {
		return this.byKey.size();
	}

	@Override
	public Optional<RegistrationInfo> registrationInfo(final ResourceKey<T> element) {
		return Optional.ofNullable((RegistrationInfo)this.registrationInfos.get(element));
	}

	@Override
	public Lifecycle registryLifecycle() {
		return this.registryLifecycle;
	}

	public Iterator<T> iterator() {
		return Iterators.transform(this.byId.iterator(), Holder::value);
	}

	@Nullable
	@Override
	public T getValue(@Nullable final Identifier key) {
		Holder.Reference<T> result = (Holder.Reference<T>)this.byLocation.get(key);
		return getValueFromNullable(result);
	}

	@Nullable
	private static <T> T getValueFromNullable(@Nullable final Holder.Reference<T> result) {
		return result != null ? result.value() : null;
	}

	@Override
	public Set<Identifier> keySet() {
		return Collections.unmodifiableSet(this.byLocation.keySet());
	}

	@Override
	public Set<ResourceKey<T>> registryKeySet() {
		return Collections.unmodifiableSet(this.byKey.keySet());
	}

	@Override
	public Set<Entry<ResourceKey<T>, T>> entrySet() {
		return Collections.unmodifiableSet(Util.mapValuesLazy(this.byKey, Holder::value).entrySet());
	}

	@Override
	public Stream<Holder.Reference<T>> listElements() {
		return this.byId.stream();
	}

	@Override
	public Stream<HolderSet.Named<T>> getTags() {
		return this.allTags.getTags();
	}

	private HolderSet.Named<T> getOrCreateTagForRegistration(final TagKey<T> tag) {
		return (HolderSet.Named<T>)this.frozenTags.computeIfAbsent(tag, this::createTag);
	}

	private HolderSet.Named<T> createTag(final TagKey<T> tag) {
		return new HolderSet.Named<>(this, tag);
	}

	@Override
	public boolean isEmpty() {
		return this.byKey.isEmpty();
	}

	@Override
	public Optional<Holder.Reference<T>> getRandom(final RandomSource random) {
		return Util.getRandomSafe(this.byId, random);
	}

	@Override
	public boolean containsKey(final Identifier key) {
		return this.byLocation.containsKey(key);
	}

	@Override
	public boolean containsKey(final ResourceKey<T> key) {
		return this.byKey.containsKey(key);
	}

	@Override
	public DataComponentLookup<T> componentLookup() {
		return (DataComponentLookup<T>)Objects.requireNonNull(this.componentLookup, "Registry not frozen yet");
	}

	@Override
	public Registry<T> freeze() {
		if (this.frozen) {
			return this;
		} else {
			this.frozen = true;
			this.byValue.forEach((value, holder) -> holder.bindValue(value));
			List<Identifier> unboundEntries = this.byKey
				.entrySet()
				.stream()
				.filter(e -> !((Holder.Reference)e.getValue()).isBound())
				.map(e -> ((ResourceKey)e.getKey()).identifier())
				.sorted()
				.toList();
			if (!unboundEntries.isEmpty()) {
				throw new IllegalStateException("Unbound values in registry " + this.key() + ": " + unboundEntries);
			} else {
				if (this.unregisteredIntrusiveHolders != null) {
					if (!this.unregisteredIntrusiveHolders.isEmpty()) {
						throw new IllegalStateException("Some intrusive holders were not registered: " + this.unregisteredIntrusiveHolders.values());
					}

					this.unregisteredIntrusiveHolders = null;
				}

				if (this.allTags.isBound()) {
					throw new IllegalStateException("Tags already present before freezing");
				} else {
					List<Identifier> unboundTags = this.frozenTags
						.entrySet()
						.stream()
						.filter(e -> !((HolderSet.Named)e.getValue()).isBound())
						.map(e -> ((TagKey)e.getKey()).location())
						.sorted()
						.toList();
					if (!unboundTags.isEmpty()) {
						throw new IllegalStateException("Unbound tags in registry " + this.key() + ": " + unboundTags);
					} else {
						this.componentLookup = new DataComponentLookup<>(this.byId);
						this.allTags = MappedRegistry.TagSet.fromMap(this.frozenTags);
						this.refreshTagsInHolders();
						return this;
					}
				}
			}
		}
	}

	@Override
	public Holder.Reference<T> createIntrusiveHolder(final T value) {
		if (this.unregisteredIntrusiveHolders == null) {
			throw new IllegalStateException("This registry can't create intrusive holders");
		} else {
			this.validateWrite();
			return (Holder.Reference<T>)this.unregisteredIntrusiveHolders.computeIfAbsent(value, v -> Holder.Reference.createIntrusive(this, (T)v));
		}
	}

	@Override
	public Optional<HolderSet.Named<T>> get(final TagKey<T> id) {
		return this.allTags.get(id);
	}

	private Holder.Reference<T> validateAndUnwrapTagElement(final TagKey<T> id, final Holder<T> value) {
		if (!value.canSerializeIn(this)) {
			throw new IllegalStateException("Can't create named set " + id + " containing value " + value + " from outside registry " + this);
		} else if (value instanceof Holder.Reference<T> reference) {
			return reference;
		} else {
			throw new IllegalStateException("Found direct holder " + value + " value in tag " + id);
		}
	}

	@Override
	public void bindTags(final Map<TagKey<T>, List<Holder<T>>> pendingTags) {
		this.validateWrite();
		pendingTags.forEach((id, values) -> this.getOrCreateTagForRegistration(id).bind(values));
	}

	private void refreshTagsInHolders() {
		Map<Holder.Reference<T>, List<TagKey<T>>> tagsForElement = new IdentityHashMap();
		this.byKey.values().forEach(h -> tagsForElement.put(h, new ArrayList()));
		this.allTags.forEach((id, values) -> {
			for (Holder<T> value : values) {
				Holder.Reference<T> reference = this.validateAndUnwrapTagElement(id, value);
				((List)tagsForElement.get(reference)).add(id);
			}
		});
		tagsForElement.forEach(Holder.Reference::bindTags);
	}

	public void bindAllTagsToEmpty() {
		this.validateWrite();
		this.frozenTags.values().forEach(e -> e.bind(List.of()));
	}

	@Override
	public HolderGetter<T> createRegistrationLookup() {
		this.validateWrite();
		return new HolderGetter<T>() {
			{
				Objects.requireNonNull(MappedRegistry.this);
			}

			@Override
			public Optional<Holder.Reference<T>> get(final ResourceKey<T> id) {
				return Optional.of(this.getOrThrow(id));
			}

			@Override
			public Holder.Reference<T> getOrThrow(final ResourceKey<T> id) {
				return MappedRegistry.this.getOrCreateHolderOrThrow(id);
			}

			@Override
			public Optional<HolderSet.Named<T>> get(final TagKey<T> id) {
				return Optional.of(this.getOrThrow(id));
			}

			@Override
			public HolderSet.Named<T> getOrThrow(final TagKey<T> id) {
				return MappedRegistry.this.getOrCreateTagForRegistration(id);
			}
		};
	}

	@Override
	public Registry.PendingTags<T> prepareTagReload(final TagLoader.LoadResult<T> tags) {
		if (!this.frozen) {
			throw new IllegalStateException("Invalid method used for tag loading");
		} else {
			Builder<TagKey<T>, HolderSet.Named<T>> pendingTagsBuilder = ImmutableMap.builder();
			final Map<TagKey<T>, List<Holder<T>>> pendingContents = new HashMap();
			tags.tags().forEach((id, contents) -> {
				HolderSet.Named<T> tagToAdd = (HolderSet.Named<T>)this.frozenTags.get(id);
				if (tagToAdd == null) {
					tagToAdd = this.createTag(id);
				}

				pendingTagsBuilder.put(id, tagToAdd);
				pendingContents.put(id, List.copyOf(contents));
			});
			final ImmutableMap<TagKey<T>, HolderSet.Named<T>> pendingTags = pendingTagsBuilder.build();
			final HolderLookup.RegistryLookup<T> patchedHolder = new HolderLookup.RegistryLookup.Delegate<T>() {
				{
					Objects.requireNonNull(MappedRegistry.this);
				}

				@Override
				public HolderLookup.RegistryLookup<T> parent() {
					return MappedRegistry.this;
				}

				@Override
				public Optional<HolderSet.Named<T>> get(final TagKey<T> id) {
					return Optional.ofNullable(pendingTags.get(id));
				}

				@Override
				public Stream<HolderSet.Named<T>> listTags() {
					return pendingTags.values().stream();
				}
			};
			return new Registry.PendingTags<T>() {
				{
					Objects.requireNonNull(MappedRegistry.this);
				}

				@Override
				public ResourceKey<? extends Registry<? extends T>> key() {
					return MappedRegistry.this.key();
				}

				@Override
				public int size() {
					return pendingContents.size();
				}

				@Override
				public HolderLookup.RegistryLookup<T> lookup() {
					return patchedHolder;
				}

				@Override
				public void apply() {
					pendingTags.forEach((id, tag) -> {
						List<Holder<T>> values = (List<Holder<T>>)pendingContents.getOrDefault(id, List.of());
						tag.bind(values);
					});
					MappedRegistry.this.allTags = MappedRegistry.TagSet.fromMap(pendingTags);
					MappedRegistry.this.refreshTagsInHolders();
				}
			};
		}
	}

	private interface TagSet<T> {
		static <T> MappedRegistry.TagSet<T> unbound() {
			return new MappedRegistry.TagSet<T>() {
				@Override
				public boolean isBound() {
					return false;
				}

				@Override
				public Optional<HolderSet.Named<T>> get(final TagKey<T> id) {
					throw new IllegalStateException("Tags not bound, trying to access " + id);
				}

				@Override
				public void forEach(final BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
					throw new IllegalStateException("Tags not bound");
				}

				@Override
				public Stream<HolderSet.Named<T>> getTags() {
					throw new IllegalStateException("Tags not bound");
				}
			};
		}

		static <T> MappedRegistry.TagSet<T> fromMap(final Map<TagKey<T>, HolderSet.Named<T>> tags) {
			return new MappedRegistry.TagSet<T>() {
				@Override
				public boolean isBound() {
					return true;
				}

				@Override
				public Optional<HolderSet.Named<T>> get(final TagKey<T> id) {
					return Optional.ofNullable((HolderSet.Named)tags.get(id));
				}

				@Override
				public void forEach(final BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
					tags.forEach(action);
				}

				@Override
				public Stream<HolderSet.Named<T>> getTags() {
					return tags.values().stream();
				}
			};
		}

		boolean isBound();

		Optional<HolderSet.Named<T>> get(TagKey<T> id);

		void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action);

		Stream<HolderSet.Named<T>> getTags();
	}
}
