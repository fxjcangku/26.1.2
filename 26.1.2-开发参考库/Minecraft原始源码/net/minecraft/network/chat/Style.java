package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public final class Style {
	public static final Style EMPTY = new Style(null, null, null, null, null, null, null, null, null, null, null);
	public static final int NO_SHADOW = 0;
	@Nullable
	private final TextColor color;
	@Nullable
	private final Integer shadowColor;
	@Nullable
	private final Boolean bold;
	@Nullable
	private final Boolean italic;
	@Nullable
	private final Boolean underlined;
	@Nullable
	private final Boolean strikethrough;
	@Nullable
	private final Boolean obfuscated;
	@Nullable
	private final ClickEvent clickEvent;
	@Nullable
	private final HoverEvent hoverEvent;
	@Nullable
	private final String insertion;
	@Nullable
	private final FontDescription font;

	private static Style create(
		final Optional<TextColor> color,
		final Optional<Integer> shadowColor,
		final Optional<Boolean> bold,
		final Optional<Boolean> italic,
		final Optional<Boolean> underlined,
		final Optional<Boolean> strikethrough,
		final Optional<Boolean> obfuscated,
		final Optional<ClickEvent> clickEvent,
		final Optional<HoverEvent> hoverEvent,
		final Optional<String> insertion,
		final Optional<FontDescription> font
	) {
		Style result = new Style(
			(TextColor)color.orElse(null),
			(Integer)shadowColor.orElse(null),
			(Boolean)bold.orElse(null),
			(Boolean)italic.orElse(null),
			(Boolean)underlined.orElse(null),
			(Boolean)strikethrough.orElse(null),
			(Boolean)obfuscated.orElse(null),
			(ClickEvent)clickEvent.orElse(null),
			(HoverEvent)hoverEvent.orElse(null),
			(String)insertion.orElse(null),
			(FontDescription)font.orElse(null)
		);
		return result.equals(EMPTY) ? EMPTY : result;
	}

	private Style(
		@Nullable final TextColor color,
		@Nullable final Integer shadowColor,
		@Nullable final Boolean bold,
		@Nullable final Boolean italic,
		@Nullable final Boolean underlined,
		@Nullable final Boolean strikethrough,
		@Nullable final Boolean obfuscated,
		@Nullable final ClickEvent clickEvent,
		@Nullable final HoverEvent hoverEvent,
		@Nullable final String insertion,
		@Nullable final FontDescription font
	) {
		this.color = color;
		this.shadowColor = shadowColor;
		this.bold = bold;
		this.italic = italic;
		this.underlined = underlined;
		this.strikethrough = strikethrough;
		this.obfuscated = obfuscated;
		this.clickEvent = clickEvent;
		this.hoverEvent = hoverEvent;
		this.insertion = insertion;
		this.font = font;
	}

	@Nullable
	public TextColor getColor() {
		return this.color;
	}

	@Nullable
	public Integer getShadowColor() {
		return this.shadowColor;
	}

	public boolean isBold() {
		return this.bold == Boolean.TRUE;
	}

	public boolean isItalic() {
		return this.italic == Boolean.TRUE;
	}

	public boolean isStrikethrough() {
		return this.strikethrough == Boolean.TRUE;
	}

	public boolean isUnderlined() {
		return this.underlined == Boolean.TRUE;
	}

	public boolean isObfuscated() {
		return this.obfuscated == Boolean.TRUE;
	}

	public boolean isEmpty() {
		return this == EMPTY;
	}

	@Nullable
	public ClickEvent getClickEvent() {
		return this.clickEvent;
	}

	@Nullable
	public HoverEvent getHoverEvent() {
		return this.hoverEvent;
	}

	@Nullable
	public String getInsertion() {
		return this.insertion;
	}

	public FontDescription getFont() {
		return (FontDescription)(this.font != null ? this.font : FontDescription.DEFAULT);
	}

	private static <T> Style checkEmptyAfterChange(final Style newStyle, @Nullable final T previous, @Nullable final T next) {
		return previous != null && next == null && newStyle.equals(EMPTY) ? EMPTY : newStyle;
	}

	public Style withColor(@Nullable final TextColor color) {
		return Objects.equals(this.color, color)
			? this
			: checkEmptyAfterChange(
				new Style(
					color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.color,
				color
			);
	}

	public Style withColor(@Nullable final ChatFormatting color) {
		return this.withColor(color != null ? TextColor.fromLegacyFormat(color) : null);
	}

	public Style withColor(final int color) {
		return this.withColor(TextColor.fromRgb(color));
	}

	public Style withShadowColor(final int shadowColor) {
		return Objects.equals(this.shadowColor, shadowColor)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.shadowColor,
				shadowColor
			);
	}

	public Style withoutShadow() {
		return this.withShadowColor(0);
	}

	public Style withBold(@Nullable final Boolean bold) {
		return Objects.equals(this.bold, bold)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.bold,
				bold
			);
	}

	public Style withItalic(@Nullable final Boolean italic) {
		return Objects.equals(this.italic, italic)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.italic,
				italic
			);
	}

	public Style withUnderlined(@Nullable final Boolean underlined) {
		return Objects.equals(this.underlined, underlined)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.underlined,
				underlined
			);
	}

	public Style withStrikethrough(@Nullable final Boolean strikethrough) {
		return Objects.equals(this.strikethrough, strikethrough)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.strikethrough,
				strikethrough
			);
	}

	public Style withObfuscated(@Nullable final Boolean obfuscated) {
		return Objects.equals(this.obfuscated, obfuscated)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.obfuscated,
				obfuscated
			);
	}

	public Style withClickEvent(@Nullable final ClickEvent clickEvent) {
		return Objects.equals(this.clickEvent, clickEvent)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.clickEvent,
				clickEvent
			);
	}

	public Style withHoverEvent(@Nullable final HoverEvent hoverEvent) {
		return Objects.equals(this.hoverEvent, hoverEvent)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					hoverEvent,
					this.insertion,
					this.font
				),
				this.hoverEvent,
				hoverEvent
			);
	}

	public Style withInsertion(@Nullable final String insertion) {
		return Objects.equals(this.insertion, insertion)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					insertion,
					this.font
				),
				this.insertion,
				insertion
			);
	}

	public Style withFont(@Nullable final FontDescription font) {
		return Objects.equals(this.font, font)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					font
				),
				this.font,
				font
			);
	}

	public Style applyFormat(final ChatFormatting format) {
		TextColor color = this.color;
		Boolean bold = this.bold;
		Boolean italic = this.italic;
		Boolean strikethrough = this.strikethrough;
		Boolean underlined = this.underlined;
		Boolean obfuscated = this.obfuscated;
		switch (format) {
			case OBFUSCATED:
				obfuscated = true;
				break;
			case BOLD:
				bold = true;
				break;
			case STRIKETHROUGH:
				strikethrough = true;
				break;
			case UNDERLINE:
				underlined = true;
				break;
			case ITALIC:
				italic = true;
				break;
			case RESET:
				return EMPTY;
			default:
				color = TextColor.fromLegacyFormat(format);
		}

		return new Style(color, this.shadowColor, bold, italic, underlined, strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
	}

	public Style applyLegacyFormat(final ChatFormatting format) {
		TextColor color = this.color;
		Boolean bold = this.bold;
		Boolean italic = this.italic;
		Boolean strikethrough = this.strikethrough;
		Boolean underlined = this.underlined;
		Boolean obfuscated = this.obfuscated;
		switch (format) {
			case OBFUSCATED:
				obfuscated = true;
				break;
			case BOLD:
				bold = true;
				break;
			case STRIKETHROUGH:
				strikethrough = true;
				break;
			case UNDERLINE:
				underlined = true;
				break;
			case ITALIC:
				italic = true;
				break;
			case RESET:
				return EMPTY;
			default:
				obfuscated = false;
				bold = false;
				strikethrough = false;
				underlined = false;
				italic = false;
				color = TextColor.fromLegacyFormat(format);
		}

		return new Style(color, this.shadowColor, bold, italic, underlined, strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
	}

	public Style applyFormats(final ChatFormatting... formats) {
		TextColor color = this.color;
		Boolean bold = this.bold;
		Boolean italic = this.italic;
		Boolean strikethrough = this.strikethrough;
		Boolean underlined = this.underlined;
		Boolean obfuscated = this.obfuscated;

		for (ChatFormatting format : formats) {
			switch (format) {
				case OBFUSCATED:
					obfuscated = true;
					break;
				case BOLD:
					bold = true;
					break;
				case STRIKETHROUGH:
					strikethrough = true;
					break;
				case UNDERLINE:
					underlined = true;
					break;
				case ITALIC:
					italic = true;
					break;
				case RESET:
					return EMPTY;
				default:
					color = TextColor.fromLegacyFormat(format);
			}
		}

		return new Style(color, this.shadowColor, bold, italic, underlined, strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
	}

	public Style applyTo(final Style other) {
		if (this == EMPTY) {
			return other;
		} else {
			return other == EMPTY
				? this
				: new Style(
					this.color != null ? this.color : other.color,
					this.shadowColor != null ? this.shadowColor : other.shadowColor,
					this.bold != null ? this.bold : other.bold,
					this.italic != null ? this.italic : other.italic,
					this.underlined != null ? this.underlined : other.underlined,
					this.strikethrough != null ? this.strikethrough : other.strikethrough,
					this.obfuscated != null ? this.obfuscated : other.obfuscated,
					this.clickEvent != null ? this.clickEvent : other.clickEvent,
					this.hoverEvent != null ? this.hoverEvent : other.hoverEvent,
					this.insertion != null ? this.insertion : other.insertion,
					this.font != null ? this.font : other.font
				);
		}
	}

	public String toString() {
		final StringBuilder result = new StringBuilder("{");

		class Collector {
			private boolean isNotFirst;

			Collector() {
				Objects.requireNonNull(Style.this);
				super();
			}

			private void prependSeparator() {
				if (this.isNotFirst) {
					result.append(',');
				}

				this.isNotFirst = true;
			}

			private void addFlagString(final String name, @Nullable final Boolean value) {
				if (value != null) {
					this.prependSeparator();
					if (!value) {
						result.append('!');
					}

					result.append(name);
				}
			}

			private void addValueString(final String name, @Nullable final Object value) {
				if (value != null) {
					this.prependSeparator();
					result.append(name);
					result.append('=');
					result.append(value);
				}
			}
		}

		Collector collector = new Collector();
		collector.addValueString("color", this.color);
		collector.addValueString("shadowColor", this.shadowColor);
		collector.addFlagString("bold", this.bold);
		collector.addFlagString("italic", this.italic);
		collector.addFlagString("underlined", this.underlined);
		collector.addFlagString("strikethrough", this.strikethrough);
		collector.addFlagString("obfuscated", this.obfuscated);
		collector.addValueString("clickEvent", this.clickEvent);
		collector.addValueString("hoverEvent", this.hoverEvent);
		collector.addValueString("insertion", this.insertion);
		collector.addValueString("font", this.font);
		result.append("}");
		return result.toString();
	}

	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		} else {
			return !(o instanceof Style style)
				? false
				: this.bold == style.bold
					&& Objects.equals(this.getColor(), style.getColor())
					&& Objects.equals(this.getShadowColor(), style.getShadowColor())
					&& this.italic == style.italic
					&& this.obfuscated == style.obfuscated
					&& this.strikethrough == style.strikethrough
					&& this.underlined == style.underlined
					&& Objects.equals(this.clickEvent, style.clickEvent)
					&& Objects.equals(this.hoverEvent, style.hoverEvent)
					&& Objects.equals(this.insertion, style.insertion)
					&& Objects.equals(this.font, style.font);
		}
	}

	public int hashCode() {
		return Objects.hash(
			new Object[]{
				this.color,
				this.shadowColor,
				this.bold,
				this.italic,
				this.underlined,
				this.strikethrough,
				this.obfuscated,
				this.clickEvent,
				this.hoverEvent,
				this.insertion
			}
		);
	}

	public static class Serializer {
		public static final MapCodec<Style> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					TextColor.CODEC.optionalFieldOf("color").forGetter(o -> Optional.ofNullable(o.color)),
					ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("shadow_color").forGetter(o -> Optional.ofNullable(o.shadowColor)),
					Codec.BOOL.optionalFieldOf("bold").forGetter(o -> Optional.ofNullable(o.bold)),
					Codec.BOOL.optionalFieldOf("italic").forGetter(o -> Optional.ofNullable(o.italic)),
					Codec.BOOL.optionalFieldOf("underlined").forGetter(o -> Optional.ofNullable(o.underlined)),
					Codec.BOOL.optionalFieldOf("strikethrough").forGetter(o -> Optional.ofNullable(o.strikethrough)),
					Codec.BOOL.optionalFieldOf("obfuscated").forGetter(o -> Optional.ofNullable(o.obfuscated)),
					ClickEvent.CODEC.optionalFieldOf("click_event").forGetter(o -> Optional.ofNullable(o.clickEvent)),
					HoverEvent.CODEC.optionalFieldOf("hover_event").forGetter(o -> Optional.ofNullable(o.hoverEvent)),
					Codec.STRING.optionalFieldOf("insertion").forGetter(o -> Optional.ofNullable(o.insertion)),
					FontDescription.CODEC.optionalFieldOf("font").forGetter(o -> Optional.ofNullable(o.font))
				)
				.apply(i, Style::create)
		);
		public static final Codec<Style> CODEC = MAP_CODEC.codec();
		public static final StreamCodec<RegistryFriendlyByteBuf, Style> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
	}
}
