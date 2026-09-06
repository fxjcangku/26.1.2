package com.mojang.blaze3d.buffers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record GpuBufferSlice(GpuBuffer buffer, long offset, long length) {
	public GpuBufferSlice slice(final long offset, final long length) {
		if (offset >= 0L && length >= 0L && offset + length <= this.length) {
			return new GpuBufferSlice(this.buffer, this.offset + offset, length);
		} else {
			throw new IllegalArgumentException(
				"Offset of " + offset + " and length " + length + " would put new slice outside existing slice's range (of " + this.offset + "," + this.length + ")"
			);
		}
	}
}
