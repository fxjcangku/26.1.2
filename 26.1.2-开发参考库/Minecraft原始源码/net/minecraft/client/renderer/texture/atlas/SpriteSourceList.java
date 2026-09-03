package net.minecraft.client.renderer.texture.atlas;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SpriteSourceList {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter ATLAS_INFO_CONVERTER = new FileToIdConverter("atlases", ".json");
	private final List<SpriteSource> sources;

	private SpriteSourceList(final List<SpriteSource> sources) {
		this.sources = sources;
	}

	public List<SpriteSource.Loader> list(final ResourceManager resourceManager) {
		final Map<Identifier, SpriteSource.DiscardableLoader> sprites = new HashMap();
		SpriteSource.Output output = new SpriteSource.Output() {
			{
				Objects.requireNonNull(SpriteSourceList.this);
			}

			@Override
			public void add(final Identifier id, final SpriteSource.DiscardableLoader sprite) {
				SpriteSource.DiscardableLoader previous = (SpriteSource.DiscardableLoader)sprites.put(id, sprite);
				if (previous != null) {
					previous.discard();
				}
			}

			@Override
			public void removeAll(final Predicate<Identifier> predicate) {
				Iterator<Entry<Identifier, SpriteSource.DiscardableLoader>> it = sprites.entrySet().iterator();

				while (it.hasNext()) {
					Entry<Identifier, SpriteSource.DiscardableLoader> entry = (Entry<Identifier, SpriteSource.DiscardableLoader>)it.next();
					if (predicate.test((Identifier)entry.getKey())) {
						((SpriteSource.DiscardableLoader)entry.getValue()).discard();
						it.remove();
					}
				}
			}
		};
		this.sources.forEach(s -> s.run(resourceManager, output));
		Builder<SpriteSource.Loader> result = ImmutableList.builder();
		result.add(loader -> MissingTextureAtlasSprite.create());
		result.addAll(sprites.values());
		return result.build();
	}

	public static SpriteSourceList load(final ResourceManager resourceManager, final Identifier atlasId) {
		Identifier resourceId = ATLAS_INFO_CONVERTER.idToFile(atlasId);
		List<SpriteSource> loaders = new ArrayList();

		for (Resource entry : resourceManager.getResourceStack(resourceId)) {
			try {
				BufferedReader reader = entry.openAsReader();

				try {
					Dynamic<JsonElement> contents = new Dynamic<>(JsonOps.INSTANCE, StrictJsonParser.parse(reader));
					loaders.addAll((Collection)SpriteSources.FILE_CODEC.parse(contents).getOrThrow());
				} catch (Throwable var10) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var9) {
							var10.addSuppressed(var9);
						}
					}

					throw var10;
				}

				if (reader != null) {
					reader.close();
				}
			} catch (Exception var11) {
				LOGGER.error("Failed to parse atlas definition {} in pack {}", resourceId, entry.sourcePackId(), var11);
			}
		}

		return new SpriteSourceList(loaders);
	}
}
