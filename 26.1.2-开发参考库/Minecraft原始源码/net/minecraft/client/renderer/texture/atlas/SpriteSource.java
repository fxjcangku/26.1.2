package net.minecraft.client.renderer.texture.atlas;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SpriteSource {
	FileToIdConverter TEXTURE_ID_CONVERTER = new FileToIdConverter("textures", ".png");

	void run(ResourceManager resourceManager, SpriteSource.Output output);

	MapCodec<? extends SpriteSource> codec();

	@Environment(EnvType.CLIENT)
	public interface DiscardableLoader extends SpriteSource.Loader {
		default void discard() {
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface Loader {
		@Nullable
		SpriteContents get(SpriteResourceLoader loader);
	}

	@Environment(EnvType.CLIENT)
	public interface Output {
		default void add(final Identifier id, final Resource resource) {
			this.add(id, loader -> loader.loadSprite(id, resource));
		}

		void add(Identifier id, SpriteSource.DiscardableLoader sprite);

		void removeAll(Predicate<Identifier> predicate);
	}
}
