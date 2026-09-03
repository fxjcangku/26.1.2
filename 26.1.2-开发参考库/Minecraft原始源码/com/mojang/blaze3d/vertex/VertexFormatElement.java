package com.mojang.blaze3d.vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record VertexFormatElement(int id, int index, VertexFormatElement.Type type, boolean normalized, int count) {
	public static final int MAX_COUNT = 32;
	private static final VertexFormatElement[] BY_ID = new VertexFormatElement[32];
	private static final List<VertexFormatElement> ELEMENTS = new ArrayList(32);
	public static final VertexFormatElement POSITION = register(0, 0, VertexFormatElement.Type.FLOAT, false, 3);
	public static final VertexFormatElement COLOR = register(1, 0, VertexFormatElement.Type.UBYTE, true, 4);
	public static final VertexFormatElement UV0 = register(2, 0, VertexFormatElement.Type.FLOAT, false, 2);
	public static final VertexFormatElement UV = UV0;
	public static final VertexFormatElement UV1 = register(3, 1, VertexFormatElement.Type.SHORT, false, 2);
	public static final VertexFormatElement UV2 = register(4, 2, VertexFormatElement.Type.SHORT, false, 2);
	public static final VertexFormatElement NORMAL = register(5, 0, VertexFormatElement.Type.BYTE, true, 3);
	public static final VertexFormatElement LINE_WIDTH = register(6, 0, VertexFormatElement.Type.FLOAT, false, 1);

	public VertexFormatElement(int id, int index, VertexFormatElement.Type type, boolean normalized, int count) {
		if (id >= 0 && id < BY_ID.length) {
			this.id = id;
			this.index = index;
			this.type = type;
			this.normalized = normalized;
			this.count = count;
		} else {
			throw new IllegalArgumentException("Element ID must be in range [0; " + BY_ID.length + ")");
		}
	}

	public static VertexFormatElement register(final int id, final int index, final VertexFormatElement.Type type, final boolean normalized, final int count) {
		VertexFormatElement element = new VertexFormatElement(id, index, type, normalized, count);
		if (BY_ID[id] != null) {
			throw new IllegalArgumentException("Duplicate element registration for: " + id);
		} else {
			BY_ID[id] = element;
			ELEMENTS.add(element);
			return element;
		}
	}

	public String toString() {
		String string = this.count + "x" + this.type + " (" + this.id + ")";
		return this.normalized ? "normalized " + string : string;
	}

	public int mask() {
		return 1 << this.id;
	}

	public int byteSize() {
		return this.type.size() * this.count;
	}

	@Nullable
	public static VertexFormatElement byId(final int id) {
		return BY_ID[id];
	}

	public static Stream<VertexFormatElement> elementsFromMask(final int mask) {
		return ELEMENTS.stream().filter(element -> (mask & element.mask()) != 0);
	}

	@Environment(EnvType.CLIENT)
	public static enum Type {
		FLOAT(4, "Float"),
		UBYTE(1, "Unsigned Byte"),
		BYTE(1, "Byte"),
		USHORT(2, "Unsigned Short"),
		SHORT(2, "Short"),
		UINT(4, "Unsigned Int"),
		INT(4, "Int");

		private final int size;
		private final String name;

		private Type(final int size, final String name) {
			this.size = size;
			this.name = name;
		}

		public int size() {
			return this.size;
		}

		public String toString() {
			return this.name;
		}
	}
}
