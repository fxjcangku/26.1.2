package net.minecraft.client.renderer.blockentity.state;

import com.mojang.math.Transformation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SignRenderState extends BlockEntityRenderState {
	public WoodType woodType = WoodType.OAK;
	@Nullable
	public SignText frontText;
	@Nullable
	public SignText backText;
	public int textLineHeight;
	public int maxTextLineWidth;
	public boolean isTextFilteringEnabled;
	public boolean drawOutline;
	public SignRenderState.SignTransformations transformations = SignRenderState.SignTransformations.IDENTITY;

	@Environment(EnvType.CLIENT)
	public record SignTransformations(Transformation body, Transformation frontText, Transformation backText) {
		public static final SignRenderState.SignTransformations IDENTITY = new SignRenderState.SignTransformations(
			Transformation.IDENTITY, Transformation.IDENTITY, Transformation.IDENTITY
		);
	}
}
