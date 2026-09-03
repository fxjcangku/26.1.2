package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.IdentifierException;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ShaderManager extends SimplePreparableReloadListener<ShaderManager.Configs> implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int MAX_LOG_LENGTH = 32768;
	public static final String SHADER_PATH = "shaders";
	private static final String SHADER_INCLUDE_PATH = "shaders/include/";
	private static final FileToIdConverter POST_CHAIN_ID_CONVERTER = FileToIdConverter.json("post_effect");
	private final TextureManager textureManager;
	private final Consumer<Exception> recoveryHandler;
	private ShaderManager.CompilationCache compilationCache = new ShaderManager.CompilationCache(ShaderManager.Configs.EMPTY);
	private final Projection postChainProjection = new Projection();
	private final ProjectionMatrixBuffer postChainProjectionMatrixBuffer = new ProjectionMatrixBuffer("post");

	public ShaderManager(final TextureManager textureManager, final Consumer<Exception> recoveryHandler) {
		this.textureManager = textureManager;
		this.recoveryHandler = recoveryHandler;
		this.postChainProjection.setupOrtho(0.1F, 1000.0F, 1.0F, 1.0F, false);
	}

	protected ShaderManager.Configs prepare(final ResourceManager manager, final ProfilerFiller profiler) {
		Builder<ShaderManager.ShaderSourceKey, String> shaderSources = ImmutableMap.builder();
		Map<Identifier, Resource> files = manager.listResources("shaders", ShaderManager::isShader);

		for (Entry<Identifier, Resource> entry : files.entrySet()) {
			Identifier location = (Identifier)entry.getKey();
			ShaderType shaderType = ShaderType.byLocation(location);
			if (shaderType != null) {
				loadShader(location, (Resource)entry.getValue(), shaderType, files, shaderSources);
			}
		}

		Builder<Identifier, PostChainConfig> postChains = ImmutableMap.builder();

		for (Entry<Identifier, Resource> entryx : POST_CHAIN_ID_CONVERTER.listMatchingResources(manager).entrySet()) {
			loadPostChain((Identifier)entryx.getKey(), (Resource)entryx.getValue(), postChains);
		}

		return new ShaderManager.Configs(shaderSources.build(), postChains.build());
	}

	private static void loadShader(
		final Identifier location,
		final Resource resource,
		final ShaderType type,
		final Map<Identifier, Resource> files,
		final Builder<ShaderManager.ShaderSourceKey, String> output
	) {
		Identifier id = type.idConverter().fileToId(location);
		GlslPreprocessor preprocessor = createPreprocessor(files, location);

		try {
			Reader reader = resource.openAsReader();

			try {
				String source = IOUtils.toString(reader);
				output.put(new ShaderManager.ShaderSourceKey(id, type), String.join("", preprocessor.process(source)));
			} catch (Throwable var11) {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable var10) {
						var11.addSuppressed(var10);
					}
				}

				throw var11;
			}

			if (reader != null) {
				reader.close();
			}
		} catch (IOException var12) {
			LOGGER.error("Failed to load shader source at {}", location, var12);
		}
	}

	private static GlslPreprocessor createPreprocessor(final Map<Identifier, Resource> files, final Identifier location) {
		final Identifier parentLocation = location.withPath(FileUtil::getFullResourcePath);
		return new GlslPreprocessor() {
			private final Set<Identifier> importedLocations = new ObjectArraySet<>();

			@Nullable
			@Override
			public String applyImport(final boolean isRelative, final String path) {
				Identifier locationx;
				try {
					if (isRelative) {
						locationx = parentLocation.withPath((UnaryOperator<String>)(parentPath -> FileUtil.normalizeResourcePath(parentPath + path)));
					} else {
						locationx = Identifier.parse(path).withPrefix("shaders/include/");
					}
				} catch (IdentifierException var8) {
					ShaderManager.LOGGER.error("Malformed GLSL import {}: {}", path, var8.getMessage());
					return "#error " + var8.getMessage();
				}

				if (!this.importedLocations.add(locationx)) {
					return null;
				} else {
					try {
						Reader importResource = ((Resource)files.get(locationx)).openAsReader();

						String var5;
						try {
							var5 = IOUtils.toString(importResource);
						} catch (Throwable var9) {
							if (importResource != null) {
								try {
									importResource.close();
								} catch (Throwable var7) {
									var9.addSuppressed(var7);
								}
							}

							throw var9;
						}

						if (importResource != null) {
							importResource.close();
						}

						return var5;
					} catch (IOException var10) {
						ShaderManager.LOGGER.error("Could not open GLSL import {}: {}", locationx, var10.getMessage());
						return "#error " + var10.getMessage();
					}
				}
			}
		};
	}

	private static void loadPostChain(final Identifier location, final Resource resource, final Builder<Identifier, PostChainConfig> output) {
		Identifier id = POST_CHAIN_ID_CONVERTER.fileToId(location);

		try {
			Reader reader = resource.openAsReader();

			try {
				JsonElement json = StrictJsonParser.parse(reader);
				output.put(id, PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(JsonSyntaxException::new));
			} catch (Throwable var8) {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable var7) {
						var8.addSuppressed(var7);
					}
				}

				throw var8;
			}

			if (reader != null) {
				reader.close();
			}
		} catch (JsonParseException | IOException var9) {
			LOGGER.error("Failed to parse post chain at {}", location, var9);
		}
	}

	private static boolean isShader(final Identifier location) {
		return ShaderType.byLocation(location) != null || location.getPath().endsWith(".glsl");
	}

	protected void apply(final ShaderManager.Configs preparations, final ResourceManager manager, final ProfilerFiller profiler) {
		ShaderManager.CompilationCache newCompilationCache = new ShaderManager.CompilationCache(preparations);
		Set<RenderPipeline> pipelinesToPreload = new HashSet(RenderPipelines.getStaticPipelines());
		List<Identifier> failedLoads = new ArrayList();
		GpuDevice device = RenderSystem.getDevice();
		device.clearPipelineCache();

		for (RenderPipeline pipeline : pipelinesToPreload) {
			CompiledRenderPipeline compiled = device.precompilePipeline(pipeline, newCompilationCache::getShaderSource);
			if (!compiled.isValid()) {
				failedLoads.add(pipeline.getLocation());
			}
		}

		if (!failedLoads.isEmpty()) {
			device.clearPipelineCache();
			throw new RuntimeException(
				"Failed to load required shader programs:\n" + (String)failedLoads.stream().map(entry -> " - " + entry).collect(Collectors.joining("\n"))
			);
		} else {
			this.compilationCache.close();
			this.compilationCache = newCompilationCache;
		}
	}

	@Override
	public String getName() {
		return "Shader Loader";
	}

	private void tryTriggerRecovery(final Exception exception) {
		if (!this.compilationCache.triggeredRecovery) {
			this.recoveryHandler.accept(exception);
			this.compilationCache.triggeredRecovery = true;
		}
	}

	@Nullable
	public PostChain getPostChain(final Identifier id, final Set<Identifier> allowedTargets) {
		try {
			return this.compilationCache.getOrLoadPostChain(id, allowedTargets);
		} catch (ShaderManager.CompilationException var4) {
			LOGGER.error("Failed to load post chain: {}", id, var4);
			this.compilationCache.postChains.put(id, Optional.empty());
			this.tryTriggerRecovery(var4);
			return null;
		}
	}

	public void close() {
		this.compilationCache.close();
		this.postChainProjectionMatrixBuffer.close();
	}

	@Nullable
	public String getShader(final Identifier id, final ShaderType type) {
		return this.compilationCache.getShaderSource(id, type);
	}

	@Environment(EnvType.CLIENT)
	private class CompilationCache implements AutoCloseable {
		private final ShaderManager.Configs configs;
		private final Map<Identifier, Optional<PostChain>> postChains;
		private boolean triggeredRecovery;

		private CompilationCache(final ShaderManager.Configs configs) {
			Objects.requireNonNull(ShaderManager.this);
			super();
			this.postChains = new HashMap();
			this.configs = configs;
		}

		@Nullable
		public PostChain getOrLoadPostChain(final Identifier id, final Set<Identifier> allowedTargets) throws ShaderManager.CompilationException {
			Optional<PostChain> cached = (Optional<PostChain>)this.postChains.get(id);
			if (cached != null) {
				return (PostChain)cached.orElse(null);
			} else {
				PostChain postChain = this.loadPostChain(id, allowedTargets);
				this.postChains.put(id, Optional.of(postChain));
				return postChain;
			}
		}

		private PostChain loadPostChain(final Identifier id, final Set<Identifier> allowedTargets) throws ShaderManager.CompilationException {
			PostChainConfig config = (PostChainConfig)this.configs.postChains.get(id);
			if (config == null) {
				throw new ShaderManager.CompilationException("Could not find post chain with id: " + id);
			} else {
				return PostChain.load(
					config, ShaderManager.this.textureManager, allowedTargets, id, ShaderManager.this.postChainProjection, ShaderManager.this.postChainProjectionMatrixBuffer
				);
			}
		}

		public void close() {
			this.postChains.values().forEach(chain -> chain.ifPresent(PostChain::close));
			this.postChains.clear();
		}

		@Nullable
		public String getShaderSource(final Identifier id, final ShaderType type) {
			return (String)this.configs.shaderSources.get(new ShaderManager.ShaderSourceKey(id, type));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class CompilationException extends Exception {
		public CompilationException(final String message) {
			super(message);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Configs(Map<ShaderManager.ShaderSourceKey, String> shaderSources, Map<Identifier, PostChainConfig> postChains) {
		public static final ShaderManager.Configs EMPTY = new ShaderManager.Configs(Map.of(), Map.of());
	}

	@Environment(EnvType.CLIENT)
	private record ShaderSourceKey(Identifier id, ShaderType type) {
		public String toString() {
			return this.id + " (" + this.type + ")";
		}
	}
}
