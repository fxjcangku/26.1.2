package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EmptyGlyph implements UnbakedGlyph {
	private final GlyphInfo info;

	public EmptyGlyph(final float advance) {
		this.info = GlyphInfo.simple(advance);
	}

	@Override
	public GlyphInfo info() {
		return this.info;
	}

	@Override
	public BakedGlyph bake(final UnbakedGlyph.Stitcher stitcher) {
		return new BakedGlyph() {
			{
				Objects.requireNonNull(EmptyGlyph.this);
			}

			@Override
			public GlyphInfo info() {
				return EmptyGlyph.this.info;
			}

			@Nullable
			@Override
			public TextRenderable.Styled createGlyph(
				final float x, final float y, final int color, final int shadowColor, final Style style, final float boldOffset, final float shadowOffset
			) {
				return null;
			}
		};
	}
}
