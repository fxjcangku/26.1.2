package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtOps implements DynamicOps<Tag> {
	public static final NbtOps INSTANCE = new NbtOps();

	private NbtOps() {
	}

	public Tag empty() {
		return EndTag.INSTANCE;
	}

	public Tag emptyList() {
		return new ListTag();
	}

	public Tag emptyMap() {
		return new CompoundTag();
	}

	public <U> U convertTo(final DynamicOps<U> outOps, final Tag input) {
		Objects.requireNonNull(input);
		Object var3 = input;

		while (true) {
			Object var10000;
			switch (var3) {
				case EndTag ignored:
					var10000 = outOps.empty();
					break;
				case ByteTag(byte var54):
					byte var34 = var54;
					if (false) {
						byte var59 = 2;
						continue;
					}

					var10000 = outOps.createByte(var34);
					break;
				case ShortTag(short var52):
					short var35 = var52;
					if (false) {
						byte var58 = 3;
						continue;
					}

					var10000 = outOps.createShort(var35);
					break;
				case IntTag(int var50):
					int var36 = var50;
					if (false) {
						byte var57 = 4;
						continue;
					}

					var10000 = outOps.createInt(var36);
					break;
				case LongTag(long var48):
					long var37 = var48;
					if (false) {
						byte var56 = 5;
						continue;
					}

					var10000 = outOps.createLong(var37);
					break;
				case FloatTag(float var46):
					float var38 = var46;
					if (false) {
						byte var55 = 6;
						continue;
					}

					var10000 = outOps.createFloat(var38);
					break;
				case DoubleTag(double var44):
					double var39 = var44;
					if (false) {
						byte var4 = 7;
						continue;
					}

					var10000 = outOps.createDouble(var39);
					break;
				case ByteArrayTag byteArrayTag:
					var10000 = outOps.createByteList(ByteBuffer.wrap(byteArrayTag.getAsByteArray()));
					break;
				case StringTag(String var40):
					var10000 = outOps.createString(var40);
					break;
				case ListTag listTag:
					var10000 = this.convertList(outOps, listTag);
					break;
				case CompoundTag compoundTag:
					var10000 = this.convertMap(outOps, compoundTag);
					break;
				case IntArrayTag intArrayTag:
					var10000 = outOps.createIntList(Arrays.stream(intArrayTag.getAsIntArray()));
					break;
				case LongArrayTag longArrayTag:
					var10000 = outOps.createLongList(Arrays.stream(longArrayTag.getAsLongArray()));
					break;
				default:
					throw new MatchException(null, null);
			}

			return (U)var10000;
		}
	}

	public DataResult<Number> getNumberValue(final Tag input) {
		return (DataResult<Number>)input.asNumber().map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a number"));
	}

	public Tag createNumeric(final Number i) {
		return DoubleTag.valueOf(i.doubleValue());
	}

	public Tag createByte(final byte value) {
		return ByteTag.valueOf(value);
	}

	public Tag createShort(final short value) {
		return ShortTag.valueOf(value);
	}

	public Tag createInt(final int value) {
		return IntTag.valueOf(value);
	}

	public Tag createLong(final long value) {
		return LongTag.valueOf(value);
	}

	public Tag createFloat(final float value) {
		return FloatTag.valueOf(value);
	}

	public Tag createDouble(final double value) {
		return DoubleTag.valueOf(value);
	}

	public Tag createBoolean(final boolean value) {
		return ByteTag.valueOf(value);
	}

	public DataResult<String> getStringValue(final Tag input) {
		return input instanceof StringTag(String var4) ? DataResult.success(var4) : DataResult.error(() -> "Not a string");
	}

	public Tag createString(final String value) {
		return StringTag.valueOf(value);
	}

	public DataResult<Tag> mergeToList(final Tag list, final Tag value) {
		return (DataResult<Tag>)createCollector(list)
			.map(collector -> DataResult.success(collector.accept(value).result()))
			.orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
	}

	public DataResult<Tag> mergeToList(final Tag list, final List<Tag> values) {
		return (DataResult<Tag>)createCollector(list)
			.map(collector -> DataResult.success(collector.acceptAll(values).result()))
			.orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
	}

	public DataResult<Tag> mergeToMap(final Tag map, final Tag key, final Tag value) {
		if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
			return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
		} else if (key instanceof StringTag(String var10)) {
			String tag = var10;
			CompoundTag output = map instanceof CompoundTag tagx ? tagx.shallowCopy() : new CompoundTag();
			output.put(tag, value);
			return DataResult.success(output);
		} else {
			return DataResult.error(() -> "key is not a string: " + key, map);
		}
	}

	public DataResult<Tag> mergeToMap(final Tag map, final MapLike<Tag> values) {
		if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
			return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
		} else {
			Iterator<Pair<Tag, Tag>> valuesIterator = values.entries().iterator();
			if (!valuesIterator.hasNext()) {
				return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
			} else {
				CompoundTag output = map instanceof CompoundTag tag ? tag.shallowCopy() : new CompoundTag();
				List<Tag> missed = new ArrayList();
				valuesIterator.forEachRemaining(entry -> {
					Tag key = (Tag)entry.getFirst();
					if (key instanceof StringTag(String patt1$temp)) {
						output.put(patt1$temp, (Tag)entry.getSecond());
					} else {
						missed.add(key);
					}
				});
				return !missed.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + missed, output) : DataResult.success(output);
			}
		}
	}

	public DataResult<Tag> mergeToMap(final Tag map, final Map<Tag, Tag> values) {
		if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
			return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
		} else if (values.isEmpty()) {
			return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
		} else {
			CompoundTag output = map instanceof CompoundTag tag ? tag.shallowCopy() : new CompoundTag();
			List<Tag> missed = new ArrayList();

			for (Entry<Tag, Tag> entry : values.entrySet()) {
				Tag key = (Tag)entry.getKey();
				if (key instanceof StringTag(String var10)) {
					output.put(var10, (Tag)entry.getValue());
				} else {
					missed.add(key);
				}
			}

			return !missed.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + missed, output) : DataResult.success(output);
		}
	}

	public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(final Tag input) {
		return input instanceof CompoundTag tag
			? DataResult.success(tag.entrySet().stream().map(entry -> Pair.of(this.createString((String)entry.getKey()), (Tag)entry.getValue())))
			: DataResult.error(() -> "Not a map: " + input);
	}

	public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(final Tag input) {
		return input instanceof CompoundTag tag ? DataResult.success(c -> {
			for (Entry<String, Tag> entry : tag.entrySet()) {
				c.accept(this.createString((String)entry.getKey()), (Tag)entry.getValue());
			}
		}) : DataResult.error(() -> "Not a map: " + input);
	}

	public DataResult<MapLike<Tag>> getMap(final Tag input) {
		return input instanceof CompoundTag tag ? DataResult.success(new MapLike<Tag>() {
			{
				Objects.requireNonNull(NbtOps.this);
			}

			@Nullable
			public Tag get(final Tag key) {
				if (key instanceof StringTag(String var4)) {
					return tag.get(var4);
				} else {
					throw new UnsupportedOperationException("Cannot get map entry with non-string key: " + key);
				}
			}

			@Nullable
			public Tag get(final String key) {
				return tag.get(key);
			}

			@Override
			public Stream<Pair<Tag, Tag>> entries() {
				return tag.entrySet().stream().map(entry -> Pair.of(NbtOps.this.createString((String)entry.getKey()), (Tag)entry.getValue()));
			}

			public String toString() {
				return "MapLike[" + tag + "]";
			}
		}) : DataResult.error(() -> "Not a map: " + input);
	}

	public Tag createMap(final Stream<Pair<Tag, Tag>> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach(entry -> {
			Tag key = (Tag)entry.getFirst();
			Tag value = (Tag)entry.getSecond();
			if (key instanceof StringTag(String patt1$temp)) {
				tag.put(patt1$temp, value);
			} else {
				throw new UnsupportedOperationException("Cannot create map with non-string key: " + key);
			}
		});
		return tag;
	}

	public DataResult<Stream<Tag>> getStream(final Tag input) {
		return input instanceof CollectionTag collection ? DataResult.success(collection.stream()) : DataResult.error(() -> "Not a list");
	}

	public DataResult<Consumer<Consumer<Tag>>> getList(final Tag input) {
		return input instanceof CollectionTag collection ? DataResult.success(collection::forEach) : DataResult.error(() -> "Not a list: " + input);
	}

	public DataResult<ByteBuffer> getByteBuffer(final Tag input) {
		return input instanceof ByteArrayTag array ? DataResult.success(ByteBuffer.wrap(array.getAsByteArray())) : DynamicOps.super.getByteBuffer(input);
	}

	public Tag createByteList(final ByteBuffer input) {
		ByteBuffer wholeBuffer = input.duplicate().clear();
		byte[] bytes = new byte[input.capacity()];
		wholeBuffer.get(0, bytes, 0, bytes.length);
		return new ByteArrayTag(bytes);
	}

	public DataResult<IntStream> getIntStream(final Tag input) {
		return input instanceof IntArrayTag array ? DataResult.success(Arrays.stream(array.getAsIntArray())) : DynamicOps.super.getIntStream(input);
	}

	public Tag createIntList(final IntStream input) {
		return new IntArrayTag(input.toArray());
	}

	public DataResult<LongStream> getLongStream(final Tag input) {
		return input instanceof LongArrayTag array ? DataResult.success(Arrays.stream(array.getAsLongArray())) : DynamicOps.super.getLongStream(input);
	}

	public Tag createLongList(final LongStream input) {
		return new LongArrayTag(input.toArray());
	}

	public Tag createList(final Stream<Tag> input) {
		return new ListTag((List<Tag>)input.collect(Util.toMutableList()));
	}

	public Tag remove(final Tag input, final String key) {
		if (input instanceof CompoundTag tag) {
			CompoundTag result = tag.shallowCopy();
			result.remove(key);
			return result;
		} else {
			return input;
		}
	}

	public String toString() {
		return "NBT";
	}

	@Override
	public RecordBuilder<Tag> mapBuilder() {
		return new NbtOps.NbtRecordBuilder();
	}

	private static Optional<NbtOps.ListCollector> createCollector(final Tag tag) {
		if (tag instanceof EndTag) {
			return Optional.of(new NbtOps.GenericListCollector());
		} else if (tag instanceof CollectionTag collection) {
			if (collection.isEmpty()) {
				return Optional.of(new NbtOps.GenericListCollector());
			} else {
				return switch (collection) {
					case ListTag list -> Optional.of(new NbtOps.GenericListCollector(list));
					case ByteArrayTag array -> Optional.of(new NbtOps.ByteListCollector(array.getAsByteArray()));
					case IntArrayTag arrayx -> Optional.of(new NbtOps.IntListCollector(arrayx.getAsIntArray()));
					case LongArrayTag arrayxx -> Optional.of(new NbtOps.LongListCollector(arrayxx.getAsLongArray()));
					default -> throw new MatchException(null, null);
				};
			}
		} else {
			return Optional.empty();
		}
	}

	private static class ByteListCollector implements NbtOps.ListCollector {
		private final ByteArrayList values = new ByteArrayList();

		public ByteListCollector(final byte[] initialValues) {
			this.values.addElements(0, initialValues);
		}

		@Override
		public NbtOps.ListCollector accept(final Tag tag) {
			if (tag instanceof ByteTag byteTag) {
				this.values.add(byteTag.byteValue());
				return this;
			} else {
				return new NbtOps.GenericListCollector(this.values).accept(tag);
			}
		}

		@Override
		public Tag result() {
			return new ByteArrayTag(this.values.toByteArray());
		}
	}

	private static class GenericListCollector implements NbtOps.ListCollector {
		private final ListTag result = new ListTag();

		private GenericListCollector() {
		}

		private GenericListCollector(final ListTag initial) {
			this.result.addAll(initial);
		}

		public GenericListCollector(final IntArrayList initials) {
			initials.forEach(v -> this.result.add(IntTag.valueOf(v)));
		}

		public GenericListCollector(final ByteArrayList initials) {
			initials.forEach(v -> this.result.add(ByteTag.valueOf(v)));
		}

		public GenericListCollector(final LongArrayList initials) {
			initials.forEach(v -> this.result.add(LongTag.valueOf(v)));
		}

		@Override
		public NbtOps.ListCollector accept(final Tag tag) {
			this.result.add(tag);
			return this;
		}

		@Override
		public Tag result() {
			return this.result;
		}
	}

	private static class IntListCollector implements NbtOps.ListCollector {
		private final IntArrayList values = new IntArrayList();

		public IntListCollector(final int[] initialValues) {
			this.values.addElements(0, initialValues);
		}

		@Override
		public NbtOps.ListCollector accept(final Tag tag) {
			if (tag instanceof IntTag intTag) {
				this.values.add(intTag.intValue());
				return this;
			} else {
				return new NbtOps.GenericListCollector(this.values).accept(tag);
			}
		}

		@Override
		public Tag result() {
			return new IntArrayTag(this.values.toIntArray());
		}
	}

	private interface ListCollector {
		NbtOps.ListCollector accept(Tag t);

		default NbtOps.ListCollector acceptAll(final Iterable<Tag> tags) {
			NbtOps.ListCollector collector = this;

			for (Tag tag : tags) {
				collector = collector.accept(tag);
			}

			return collector;
		}

		Tag result();
	}

	private static class LongListCollector implements NbtOps.ListCollector {
		private final LongArrayList values = new LongArrayList();

		public LongListCollector(final long[] initialValues) {
			this.values.addElements(0, initialValues);
		}

		@Override
		public NbtOps.ListCollector accept(final Tag tag) {
			if (tag instanceof LongTag longTag) {
				this.values.add(longTag.longValue());
				return this;
			} else {
				return new NbtOps.GenericListCollector(this.values).accept(tag);
			}
		}

		@Override
		public Tag result() {
			return new LongArrayTag(this.values.toLongArray());
		}
	}

	private class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
		protected NbtRecordBuilder() {
			Objects.requireNonNull(NbtOps.this);
			super(NbtOps.this);
		}

		protected CompoundTag initBuilder() {
			return new CompoundTag();
		}

		protected CompoundTag append(final String key, final Tag value, final CompoundTag builder) {
			builder.put(key, value);
			return builder;
		}

		protected DataResult<Tag> build(final CompoundTag builder, final Tag prefix) {
			if (prefix == null || prefix == EndTag.INSTANCE) {
				return DataResult.success(builder);
			} else if (!(prefix instanceof CompoundTag compound)) {
				return DataResult.error(() -> "mergeToMap called with not a map: " + prefix, prefix);
			} else {
				CompoundTag result = compound.shallowCopy();

				for (Entry<String, Tag> entry : builder.entrySet()) {
					result.put((String)entry.getKey(), (Tag)entry.getValue());
				}

				return DataResult.success(result);
			}
		}
	}
}
