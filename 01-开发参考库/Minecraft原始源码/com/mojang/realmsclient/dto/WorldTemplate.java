package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record WorldTemplate(
	String id,
	String name,
	String version,
	String author,
	String link,
	@Nullable String image,
	String trailer,
	String recommendedPlayers,
	WorldTemplate.WorldTemplateType type
) {
	private static final Logger LOGGER = LogUtils.getLogger();

	@Nullable
	public static WorldTemplate parse(final JsonObject node) {
		try {
			String templateTypeName = JsonUtils.getStringOr("type", node, null);
			return new WorldTemplate(
				JsonUtils.getStringOr("id", node, ""),
				JsonUtils.getStringOr("name", node, ""),
				JsonUtils.getStringOr("version", node, ""),
				JsonUtils.getStringOr("author", node, ""),
				JsonUtils.getStringOr("link", node, ""),
				JsonUtils.getStringOr("image", node, null),
				JsonUtils.getStringOr("trailer", node, ""),
				JsonUtils.getStringOr("recommendedPlayers", node, ""),
				templateTypeName == null ? WorldTemplate.WorldTemplateType.WORLD_TEMPLATE : WorldTemplate.WorldTemplateType.valueOf(templateTypeName)
			);
		} catch (Exception var2) {
			LOGGER.error("Could not parse WorldTemplate", (Throwable)var2);
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum WorldTemplateType {
		WORLD_TEMPLATE,
		MINIGAME,
		ADVENTUREMAP,
		EXPERIENCE,
		INSPIRATION;
	}
}
