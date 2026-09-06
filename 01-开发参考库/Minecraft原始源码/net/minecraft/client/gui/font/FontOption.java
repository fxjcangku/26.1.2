package net.minecraft.client.gui.font;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public enum FontOption implements StringRepresentable {
	UNIFORM("uniform"),
	JAPANESE_VARIANTS("jp");

	public static final Codec<FontOption> CODEC = StringRepresentable.fromEnum(FontOption::values);
	private final String name;

	private FontOption(final String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	@Environment(EnvType.CLIENT)
	public static class Filter {
		private final Map<FontOption, Boolean> values;
		public static final Codec<FontOption.Filter> CODEC = Codec.unboundedMap(FontOption.CODEC, Codec.BOOL).xmap(FontOption.Filter::new, p -> p.values);
		public static final FontOption.Filter ALWAYS_PASS = new FontOption.Filter(Map.of());

		public Filter(final Map<FontOption, Boolean> values) {
			this.values = values;
		}

		public boolean apply(final Set<FontOption> options) {
			for (Entry<FontOption, Boolean> e : this.values.entrySet()) {
				if (options.contains(e.getKey()) != (Boolean)e.getValue()) {
					return false;
				}
			}

			return true;
		}

		public FontOption.Filter merge(final FontOption.Filter other) {
			Map<FontOption, Boolean> options = new HashMap(other.values);
			options.putAll(this.values);
			return new FontOption.Filter(Map.copyOf(options));
		}
	}
}
