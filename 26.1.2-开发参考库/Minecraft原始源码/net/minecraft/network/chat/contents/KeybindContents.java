package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

public class KeybindContents implements ComponentContents {
	public static final MapCodec<KeybindContents> MAP_CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(Codec.STRING.fieldOf("keybind").forGetter(o -> o.name)).apply(i, KeybindContents::new)
	);
	private final String name;
	@Nullable
	private Supplier<Component> nameResolver;

	public KeybindContents(final String name) {
		this.name = name;
	}

	private Component getNestedComponent() {
		if (this.nameResolver == null) {
			this.nameResolver = (Supplier<Component>)KeybindResolver.keyResolver.apply(this.name);
		}

		return (Component)this.nameResolver.get();
	}

	@Override
	public <T> Optional<T> visit(final FormattedText.ContentConsumer<T> output) {
		return this.getNestedComponent().visit(output);
	}

	@Override
	public <T> Optional<T> visit(final FormattedText.StyledContentConsumer<T> output, final Style currentStyle) {
		return this.getNestedComponent().visit(output, currentStyle);
	}

	public boolean equals(final Object o) {
		return this == o ? true : o instanceof KeybindContents that && this.name.equals(that.name);
	}

	public int hashCode() {
		return this.name.hashCode();
	}

	public String toString() {
		return "keybind{" + this.name + "}";
	}

	public String getName() {
		return this.name;
	}

	@Override
	public MapCodec<KeybindContents> codec() {
		return MAP_CODEC;
	}
}
