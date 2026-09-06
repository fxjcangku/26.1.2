package net.minecraft.client.sounds;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class SoundBufferLibrary {
	private final ResourceProvider resourceManager;
	private final Map<Identifier, CompletableFuture<SoundBuffer>> cache = Maps.<Identifier, CompletableFuture<SoundBuffer>>newHashMap();

	public SoundBufferLibrary(final ResourceProvider resourceProvider) {
		this.resourceManager = resourceProvider;
	}

	public CompletableFuture<SoundBuffer> getCompleteBuffer(final Identifier location) {
		return (CompletableFuture<SoundBuffer>)this.cache.computeIfAbsent(location, l -> CompletableFuture.supplyAsync(() -> {
			try {
				InputStream is = this.resourceManager.open(l);

				SoundBuffer x2;
				try {
					FiniteAudioStream as = new JOrbisAudioStream(is);

					try {
						ByteBuffer data = as.readAll();
						x2 = new SoundBuffer(data, as.getFormat());
					} catch (Throwable var8) {
						try {
							as.close();
						} catch (Throwable var7) {
							var8.addSuppressed(var7);
						}

						throw var8;
					}

					as.close();
				} catch (Throwable var9) {
					if (is != null) {
						try {
							is.close();
						} catch (Throwable var6) {
							var9.addSuppressed(var6);
						}
					}

					throw var9;
				}

				if (is != null) {
					is.close();
				}

				return x2;
			} catch (IOException var10) {
				throw new CompletionException(var10);
			}
		}, Util.nonCriticalIoPool()));
	}

	public CompletableFuture<AudioStream> getStream(final Identifier location, final boolean looping) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				InputStream is = this.resourceManager.open(location);
				return (AudioStream)(looping ? new LoopingAudioStream(JOrbisAudioStream::new, is) : new JOrbisAudioStream(is));
			} catch (IOException var4) {
				throw new CompletionException(var4);
			}
		}, Util.nonCriticalIoPool());
	}

	public void clear() {
		this.cache.values().forEach(future -> future.thenAccept(SoundBuffer::discardAlBuffer));
		this.cache.clear();
	}

	public CompletableFuture<?> preload(final Collection<Sound> sounds) {
		return CompletableFuture.allOf((CompletableFuture[])sounds.stream().map(sound -> this.getCompleteBuffer(sound.getPath())).toArray(CompletableFuture[]::new));
	}

	public void enumerate(final SoundBufferLibrary.DebugOutput debugOutput) {
		this.cache.forEach((id, bufferFuture) -> {
			SoundBuffer buffer = (SoundBuffer)bufferFuture.getNow(null);
			if (buffer != null && buffer.isValid()) {
				debugOutput.accountBuffer(id, buffer.size(), buffer.format());
			}
		});
	}

	@Environment(EnvType.CLIENT)
	public interface DebugOutput {
		void accountBuffer(Identifier id, int size, AudioFormat format);

		@Environment(EnvType.CLIENT)
		public static class Counter implements SoundBufferLibrary.DebugOutput {
			private int totalCount;
			private long totalSize;

			@Override
			public void accountBuffer(final Identifier id, final int size, final AudioFormat format) {
				this.totalCount++;
				this.totalSize += size;
			}

			public int totalCount() {
				return this.totalCount;
			}

			public long totalSize() {
				return this.totalSize;
			}
		}
	}
}
