package net.minecraft.client.renderer.block.dispatch;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

@Environment(EnvType.CLIENT)
public class WeightedVariants implements BlockStateModel {
	private final WeightedList<BlockStateModel> list;
	private final Material.Baked particleMaterial;
	@BakedQuad.MaterialFlags
	private final int materialFlags;

	public WeightedVariants(final WeightedList<BlockStateModel> list) {
		this.list = list;
		BlockStateModel firstModel = (BlockStateModel)((Weighted)list.unwrap().getFirst()).value();
		this.particleMaterial = firstModel.particleMaterial();
		this.materialFlags = computeMaterialFlags(list);
	}

	@BakedQuad.MaterialFlags
	private static int computeMaterialFlags(final WeightedList<BlockStateModel> list) {
		int flags = 0;

		for (Weighted<BlockStateModel> entry : list.unwrap()) {
			flags |= entry.value().materialFlags();
		}

		return flags;
	}

	@Override
	public Material.Baked particleMaterial() {
		return this.particleMaterial;
	}

	@BakedQuad.MaterialFlags
	@Override
	public int materialFlags() {
		return this.materialFlags;
	}

	@Override
	public void collectParts(final RandomSource random, final List<BlockStateModelPart> output) {
		this.list.getRandomOrThrow(random).collectParts(random, output);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(WeightedList<BlockStateModel.Unbaked> entries) implements BlockStateModel.Unbaked {
		@Override
		public BlockStateModel bake(final ModelBaker modelBakery) {
			return new WeightedVariants(this.entries.map(m -> m.bake(modelBakery)));
		}

		@Override
		public void resolveDependencies(final ResolvableModel.Resolver resolver) {
			this.entries.unwrap().forEach(v -> ((BlockStateModel.Unbaked)v.value()).resolveDependencies(resolver));
		}
	}
}
