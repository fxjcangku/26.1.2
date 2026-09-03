package net.minecraft.client.renderer.state;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MapRenderState {
	@Nullable
	public Identifier texture;
	public final List<MapRenderState.MapDecorationRenderState> decorations = new ArrayList();

	@Environment(EnvType.CLIENT)
	public static class MapDecorationRenderState {
		@Nullable
		public TextureAtlasSprite atlasSprite;
		public byte x;
		public byte y;
		public byte rot;
		public boolean renderOnFrame;
		@Nullable
		public Component name;
	}
}
