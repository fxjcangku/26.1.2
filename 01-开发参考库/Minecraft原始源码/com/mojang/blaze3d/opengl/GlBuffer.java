package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.jtracy.MemoryPool;
import com.mojang.jtracy.TracyClient;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlBuffer extends GpuBuffer {
	protected static final MemoryPool MEMORY_POOl = TracyClient.createMemoryPool("GPU Buffers");
	protected boolean closed;
	@Nullable
	protected final Supplier<String> label;
	private final DirectStateAccess dsa;
	protected final int handle;
	@Nullable
	protected ByteBuffer persistentBuffer;

	protected GlBuffer(
		@Nullable final Supplier<String> label,
		final DirectStateAccess dsa,
		@GpuBuffer.Usage final int usage,
		final long size,
		final int handle,
		@Nullable final ByteBuffer persistentBuffer
	) {
		super(usage, size);
		this.label = label;
		this.dsa = dsa;
		this.handle = handle;
		this.persistentBuffer = persistentBuffer;
		int clampedSize = (int)Math.min(size, 2147483647L);
		MEMORY_POOl.malloc(handle, clampedSize);
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			if (this.persistentBuffer != null) {
				this.dsa.unmapBuffer(this.handle, this.usage());
				this.persistentBuffer = null;
			}

			GlStateManager._glDeleteBuffers(this.handle);
			MEMORY_POOl.free(this.handle);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class GlMappedView implements GpuBuffer.MappedView {
		private final Runnable unmap;
		private final GlBuffer buffer;
		private final ByteBuffer data;
		private boolean closed;

		protected GlMappedView(final Runnable unmap, final GlBuffer buffer, final ByteBuffer data) {
			this.unmap = unmap;
			this.buffer = buffer;
			this.data = data;
		}

		@Override
		public ByteBuffer data() {
			return this.data;
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				this.unmap.run();
			}
		}
	}
}
