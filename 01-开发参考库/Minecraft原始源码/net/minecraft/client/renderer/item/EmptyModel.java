package net.minecraft.client.renderer.item;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EmptyModel implements ItemModel {
	public static final ItemModel INSTANCE = new EmptyModel();

	@Override
	public void update(
		final ItemStackRenderState output,
		final ItemStack item,
		final ItemModelResolver resolver,
		final ItemDisplayContext displayContext,
		@Nullable final ClientLevel level,
		@Nullable final ItemOwner owner,
		final int seed
	) {
		output.appendModelIdentityElement(this);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements ItemModel.Unbaked {
		public static final MapCodec<EmptyModel.Unbaked> MAP_CODEC = MapCodec.unit(EmptyModel.Unbaked::new);

		@Override
		public void resolveDependencies(final ResolvableModel.Resolver resolver) {
		}

		@Override
		public ItemModel bake(final ItemModel.BakingContext context, final Matrix4fc transformation) {
			return EmptyModel.INSTANCE;
		}

		@Override
		public MapCodec<EmptyModel.Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
