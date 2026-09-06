package net.minecraft.world.level.saveddata;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;

public record SavedDataType<T extends SavedData>(Identifier id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType) {
	public boolean equals(final Object obj) {
		return obj instanceof SavedDataType<?> type && this.id.equals(type.id);
	}

	public int hashCode() {
		return this.id.hashCode();
	}

	public String toString() {
		return "SavedDataType[" + this.id + "]";
	}
}
