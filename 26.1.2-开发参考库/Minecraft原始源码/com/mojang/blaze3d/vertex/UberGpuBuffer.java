package com.mojang.blaze3d.vertex;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class UberGpuBuffer<T> implements AutoCloseable {
	private final int alignSize;
	private final UberGpuBuffer.UberGpuBufferStagingBuffer stagingBuffer;
	private int stagingBufferUsedSize;
	private final String name;
	private final List<Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap>> nodes;
	private final Object2ObjectOpenHashMap<T, UberGpuBuffer.StagedAllocationEntry<T>> stagedAllocations = new Object2ObjectOpenHashMap<>(32);
	private final ObjectOpenHashSet<T> skippedStagedAllocations = new ObjectOpenHashSet<>(32);
	private final Map<T, TlsfAllocator.Allocation> allocationMap = new HashMap(256);

	public UberGpuBuffer(
		final String name,
		final int usage,
		final int heapSize,
		final int alignSize,
		final GpuDevice gpuDevice,
		final int stagingBufferSize,
		final GraphicsWorkarounds workarounds
	) {
		if (stagingBufferSize > heapSize) {
			throw new IllegalArgumentException("Staging buffer size cannot be bigger than heap size");
		} else {
			this.name = "UberBuffer " + name;
			this.stagingBuffer = UberGpuBuffer.UberGpuBufferStagingBuffer.create(this.name, gpuDevice, stagingBufferSize, workarounds);
			this.stagingBufferUsedSize = 0;
			this.nodes = new ArrayList();
			this.alignSize = alignSize;
			String initialHeapName = this.name + " 0";
			UberGpuBuffer.UberGpuBufferHeap initialHeap = new UberGpuBuffer.UberGpuBufferHeap(heapSize, gpuDevice, usage, initialHeapName);
			TlsfAllocator initialTlsfAllocator = new TlsfAllocator(initialHeap);
			this.nodes.add(new Pair<>(initialTlsfAllocator, initialHeap));
		}
	}

	public boolean addAllocation(final T allocationKey, @Nullable final UberGpuBuffer.UploadCallback<T> callback, final ByteBuffer buffer) {
		int startOffset = this.stagingBufferUsedSize;
		ByteBuffer stagingBuffer = this.stagingBuffer.getStagingBuffer();
		if (buffer.remaining() > stagingBuffer.capacity()) {
			throw new IllegalArgumentException("UberGpuBuffer cannot have any allocations bigger than its staging buffer, increase the staging buffer size!");
		} else if (buffer.remaining() > stagingBuffer.capacity() - startOffset) {
			return false;
		} else {
			MemoryUtil.memCopy(buffer, stagingBuffer.position(startOffset));
			this.stagingBufferUsedSize = this.stagingBufferUsedSize + buffer.remaining();
			UberGpuBuffer.StagedAllocationEntry<T> entry = new UberGpuBuffer.StagedAllocationEntry<>(callback, (long)startOffset, (long)buffer.remaining());
			this.stagedAllocations.put(allocationKey, entry);
			return true;
		}
	}

	public boolean uploadStagedAllocations(final GpuDevice gpuDevice, final CommandEncoder encoder) {
		for (T key : this.stagedAllocations.keySet()) {
			this.freeAllocation(key);
		}

		boolean newHeapCreatedOrDestroyed = false;

		try (Zone ignored = Profiler.get().zone("Upload staged allocations")) {
			for (Entry<T, UberGpuBuffer.StagedAllocationEntry<T>> entry : this.stagedAllocations.entrySet()) {
				long allocationSize = ((UberGpuBuffer.StagedAllocationEntry)entry.getValue()).size;
				if (!this.skippedStagedAllocations.contains(entry.getKey())) {
					TlsfAllocator.Allocation allocation = null;

					for (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node : this.nodes) {
						allocation = node.getFirst().allocate(allocationSize, this.alignSize);
						if (allocation != null) {
							break;
						}
					}

					if (allocation == null) {
						try (Zone ignored2 = Profiler.get().zone("Create new heap")) {
							UberGpuBuffer.UberGpuBufferHeap firstHeap = (UberGpuBuffer.UberGpuBufferHeap)((Pair)this.nodes.getFirst()).getSecond();
							long heapSize = firstHeap.gpuBuffer.size();

							assert allocationSize <= heapSize;

							String heapName = String.format(Locale.ROOT, "%s %d", this.name, this.nodes.size());
							UberGpuBuffer.UberGpuBufferHeap newHeap = new UberGpuBuffer.UberGpuBufferHeap(heapSize, gpuDevice, firstHeap.gpuBuffer.usage(), heapName);
							TlsfAllocator newTlsfAllocator = new TlsfAllocator(newHeap);
							this.nodes.add(new Pair<>(newTlsfAllocator, newHeap));
							allocation = newTlsfAllocator.allocate(allocationSize, this.alignSize);
							newHeapCreatedOrDestroyed = true;
						}
					}

					if (allocation != null) {
						TlsfAllocator.Heap allocationHeap = allocation.getHeap();
						GpuBuffer allocationDestBuffer = ((UberGpuBuffer.UberGpuBufferHeap)allocationHeap).gpuBuffer;
						this.stagingBuffer
							.copyToHeap(
								encoder, allocationDestBuffer, allocation.getOffsetFromHeap(), ((UberGpuBuffer.StagedAllocationEntry)entry.getValue()).offset, allocationSize
							);
						this.allocationMap.put(entry.getKey(), allocation);
						if (((UberGpuBuffer.StagedAllocationEntry)entry.getValue()).callback != null) {
							((UberGpuBuffer.StagedAllocationEntry)entry.getValue()).callback.bufferHasBeenUploaded((T)entry.getKey());
						}
					}
				}
			}

			this.stagingBuffer.clearFrame(encoder);
			this.stagingBufferUsedSize = 0;
			this.stagedAllocations.clear();
			this.skippedStagedAllocations.clear();
		}

		Iterator<Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap>> iterator = this.nodes.iterator();

		while (iterator.hasNext() && this.nodes.size() > 1) {
			Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> nodex = (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap>)iterator.next();
			if (nodex.getFirst().isCompletelyFree()) {
				nodex.getSecond().gpuBuffer.close();
				iterator.remove();
				newHeapCreatedOrDestroyed = true;
				break;
			}
		}

		return newHeapCreatedOrDestroyed;
	}

	@Nullable
	public TlsfAllocator.Allocation getAllocation(final T allocationKey) {
		return (TlsfAllocator.Allocation)this.allocationMap.get(allocationKey);
	}

	public void removeAllocation(final T allocationKey) {
		this.skippedStagedAllocations.add(allocationKey);
		this.freeAllocation(allocationKey);
	}

	private void freeAllocation(final T allocationKey) {
		TlsfAllocator.Allocation allocation = (TlsfAllocator.Allocation)this.allocationMap.remove(allocationKey);
		if (allocation != null) {
			for (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node : this.nodes) {
				if (node.getSecond() == allocation.getHeap()) {
					node.getFirst().free(allocation);
					break;
				}
			}
		}
	}

	public GpuBuffer getGpuBuffer(final TlsfAllocator.Allocation allocation) {
		return ((UberGpuBuffer.UberGpuBufferHeap)allocation.getHeap()).gpuBuffer;
	}

	@VisibleForDebug
	public void printStatistics() {
		for (int i = 0; i < this.nodes.size(); i++) {
			Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node = (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap>)this.nodes.get(i);
			String heapName = String.format(Locale.ROOT, "%s %d", this.name, i);
			node.getFirst().printAllocatorStatistics(heapName);
		}
	}

	public void close() {
		this.stagingBuffer.destroyBuffer();
		this.stagingBufferUsedSize = 0;
		this.stagedAllocations.clear();
		this.allocationMap.clear();

		for (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node : this.nodes) {
			node.getSecond().gpuBuffer.close();
		}

		this.nodes.clear();
	}

	@Environment(EnvType.CLIENT)
	private static class StagedAllocationEntry<T> {
		@Nullable
		UberGpuBuffer.UploadCallback<T> callback;
		long offset;
		long size;

		private StagedAllocationEntry(@Nullable final UberGpuBuffer.UploadCallback<T> callback, final long offset, final long size) {
			this.offset = offset;
			this.size = size;
			this.callback = callback;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class UberGpuBufferHeap extends TlsfAllocator.Heap {
		GpuBuffer gpuBuffer;

		UberGpuBufferHeap(final long size, final GpuDevice gpuDevice, final int usage, final String name) {
			super(size);
			this.gpuBuffer = gpuDevice.createBuffer(() -> name, usage | 8 | 16, size);
		}
	}

	@Environment(EnvType.CLIENT)
	private abstract static class UberGpuBufferStagingBuffer {
		public static UberGpuBuffer.UberGpuBufferStagingBuffer create(
			final String name, final GpuDevice gpuDevice, final int stagingBufferSize, final GraphicsWorkarounds workarounds
		) {
			return (UberGpuBuffer.UberGpuBufferStagingBuffer)(!workarounds.isGlOnDx12()
				? new UberGpuBuffer.UberGpuBufferStagingBuffer.CPUStagingBuffer(name, gpuDevice, stagingBufferSize)
				: new UberGpuBuffer.UberGpuBufferStagingBuffer.MappedStagingBuffer(name, gpuDevice, stagingBufferSize));
		}

		abstract ByteBuffer getStagingBuffer();

		abstract void copyToHeap(final CommandEncoder encoder, final GpuBuffer heapBuffer, long heapOffset, long stagingBufferOffset, long copySize);

		abstract void clearFrame(final CommandEncoder encoder);

		abstract void destroyBuffer();

		@Environment(EnvType.CLIENT)
		private static class CPUStagingBuffer extends UberGpuBuffer.UberGpuBufferStagingBuffer {
			private final ByteBuffer stagingBuffer;

			private CPUStagingBuffer(final String name, final GpuDevice gpuDevice, final int stagingBufferSize) {
				this.stagingBuffer = MemoryUtil.memAlloc(stagingBufferSize);
			}

			@Override
			ByteBuffer getStagingBuffer() {
				return this.stagingBuffer;
			}

			@Override
			void copyToHeap(final CommandEncoder encoder, final GpuBuffer heapBuffer, final long heapOffset, final long stagingBufferOffset, final long copySize) {
				encoder.writeToBuffer(heapBuffer.slice(heapOffset, copySize), this.stagingBuffer.slice((int)stagingBufferOffset, (int)copySize));
			}

			@Override
			void clearFrame(final CommandEncoder encoder) {
				this.stagingBuffer.clear();
			}

			@Override
			void destroyBuffer() {
				this.stagingBuffer.clear();
				MemoryUtil.memFree(this.stagingBuffer);
			}
		}

		@Environment(EnvType.CLIENT)
		private static class MappedStagingBuffer extends UberGpuBuffer.UberGpuBufferStagingBuffer {
			private final MappableRingBuffer mappableRingBuffer;
			private GpuBuffer.MappedView currentMappedView;
			private GpuBuffer currentGPUBuffer;
			private ByteBuffer currentBuffer;

			private MappedStagingBuffer(final String name, final GpuDevice gpuDevice, final int stagingBufferSize) {
				String stagingBufferName = name + " staging buffer";
				this.mappableRingBuffer = new MappableRingBuffer(() -> stagingBufferName, 18, stagingBufferSize / 2);
				CommandEncoder encoder = gpuDevice.createCommandEncoder();
				this.currentGPUBuffer = this.mappableRingBuffer.currentBuffer();
				this.currentMappedView = encoder.mapBuffer(this.currentGPUBuffer, false, true);
				this.currentBuffer = this.currentMappedView.data();
			}

			@Override
			ByteBuffer getStagingBuffer() {
				return this.currentBuffer;
			}

			@Override
			void copyToHeap(final CommandEncoder encoder, final GpuBuffer heapBuffer, final long heapOffset, final long stagingBufferOffset, final long copySize) {
				encoder.copyToBuffer(this.currentGPUBuffer.slice(stagingBufferOffset, copySize), heapBuffer.slice(heapOffset, copySize));
			}

			@Override
			void clearFrame(final CommandEncoder encoder) {
				this.currentMappedView.close();
				this.mappableRingBuffer.rotate();
				this.currentGPUBuffer = this.mappableRingBuffer.currentBuffer();
				this.currentMappedView = encoder.mapBuffer(this.currentGPUBuffer, false, true);
				this.currentBuffer = this.currentMappedView.data();
			}

			@Override
			void destroyBuffer() {
				this.currentMappedView.close();
				this.mappableRingBuffer.close();
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public interface UploadCallback<T> {
		void bufferHasBeenUploaded(T key);
	}
}
