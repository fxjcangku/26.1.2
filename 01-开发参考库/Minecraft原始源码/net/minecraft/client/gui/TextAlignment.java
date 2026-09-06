package net.minecraft.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public enum TextAlignment {
	LEFT {
		@Override
		public int calculateLeft(final int anchor, final int width) {
			return anchor;
		}

		@Override
		public int calculateLeft(final int anchor, final Font font, final FormattedCharSequence text) {
			return anchor;
		}
	},
	CENTER {
		@Override
		public int calculateLeft(final int anchor, final int width) {
			return anchor - width / 2;
		}
	},
	RIGHT {
		@Override
		public int calculateLeft(final int anchor, final int width) {
			return anchor - width;
		}
	};

	public abstract int calculateLeft(int anchor, int width);

	public int calculateLeft(final int anchor, final Font font, final FormattedCharSequence text) {
		return this.calculateLeft(anchor, font.width(text));
	}
}
