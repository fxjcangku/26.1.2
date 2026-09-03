package net.minecraft.client.renderer.block.model;

import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fc;

@Environment(EnvType.CLIENT)
public interface BlockModel {
	void update(BlockModelRenderState output, BlockState blockState, BlockDisplayContext displayContext, long seed);

	@Environment(EnvType.CLIENT)
	public record BakingContext(
		EntityModelSet entityModelSet,
		SpriteGetter sprites,
		PlayerSkinRenderCache playerSkinRenderCache,
		Function<BlockState, BlockStateModel> modelGetter,
		BlockModel missingBlockModel
	) implements SpecialModelRenderer.BakingContext {
	}

	@Environment(EnvType.CLIENT)
	public interface Unbaked {
		BlockModel bake(BlockModel.BakingContext context, Matrix4fc transformation);
	}
}
