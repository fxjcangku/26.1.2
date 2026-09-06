package net.minecraft.client.resources.model;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientItemInfoLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter LISTER = FileToIdConverter.json("items");

	public static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> scheduleLoad(final ResourceManager manager, final Executor executor) {
		RegistryAccess.Frozen staticRegistries = ClientRegistryLayer.createRegistryAccess().compositeAccess();
		return CompletableFuture.supplyAsync(() -> LISTER.listMatchingResources(manager), executor)
			.thenCompose(
				resources -> {
					List<CompletableFuture<ClientItemInfoLoader.PendingLoad>> pendingLoads = new ArrayList(resources.size());
					resources.forEach(
						(resourceId, resource) -> pendingLoads.add(
							CompletableFuture.supplyAsync(
								() -> {
									Identifier modelId = LISTER.fileToId(resourceId);

									try {
										Reader reader = resource.openAsReader();

										ClientItemInfoLoader.PendingLoad var8;
										try {
											PlaceholderLookupProvider lookup = new PlaceholderLookupProvider(staticRegistries);
											DynamicOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
											ClientItem parsedInfo = (ClientItem)ClientItem.CODEC
												.parse(ops, StrictJsonParser.parse(reader))
												.ifError(error -> LOGGER.error("Couldn't parse item model '{}' from pack '{}': {}", modelId, resource.sourcePackId(), error.message()))
												.result()
												.map(clientItem -> lookup.hasRegisteredPlaceholders() ? clientItem.withRegistrySwapper(lookup.createSwapper()) : clientItem)
												.orElse(null);
											var8 = new ClientItemInfoLoader.PendingLoad(modelId, parsedInfo);
										} catch (Throwable var10) {
											if (reader != null) {
												try {
													reader.close();
												} catch (Throwable var9) {
													var10.addSuppressed(var9);
												}
											}

											throw var10;
										}

										if (reader != null) {
											reader.close();
										}

										return var8;
									} catch (Exception var11) {
										LOGGER.error("Failed to open item model {} from pack '{}'", resourceId, resource.sourcePackId(), var11);
										return new ClientItemInfoLoader.PendingLoad(modelId, null);
									}
								},
								executor
							)
						)
					);
					return Util.sequence(pendingLoads).thenApply(loads -> {
						Map<Identifier, ClientItem> resultMap = new HashMap();

						for (ClientItemInfoLoader.PendingLoad load : loads) {
							if (load.clientItemInfo != null) {
								resultMap.put(load.id, load.clientItemInfo);
							}
						}

						return new ClientItemInfoLoader.LoadedClientInfos(resultMap);
					});
				}
			);
	}

	@Environment(EnvType.CLIENT)
	public record LoadedClientInfos(Map<Identifier, ClientItem> contents) {
	}

	@Environment(EnvType.CLIENT)
	private record PendingLoad(Identifier id, @Nullable ClientItem clientItemInfo) {
	}
}
