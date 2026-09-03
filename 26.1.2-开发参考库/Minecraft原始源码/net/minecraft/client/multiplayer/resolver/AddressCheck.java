package net.minecraft.client.multiplayer.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.mojang.blocklist.BlockListSupplier;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface AddressCheck {
	boolean isAllowed(ResolvedServerAddress address);

	boolean isAllowed(ServerAddress address);

	static AddressCheck createFromService() {
		final ImmutableList<Predicate<String>> blockLists = (ImmutableList<Predicate<String>>)Streams.stream(ServiceLoader.load(BlockListSupplier.class))
			.map(BlockListSupplier::createBlockList)
			.filter(Objects::nonNull)
			.collect(ImmutableList.toImmutableList());
		return new AddressCheck() {
			@Override
			public boolean isAllowed(final ResolvedServerAddress address) {
				String hostName = address.getHostName();
				String hostIp = address.getHostIp();
				return blockLists.stream().noneMatch(p -> p.test(hostName) || p.test(hostIp));
			}

			@Override
			public boolean isAllowed(final ServerAddress address) {
				String hostName = address.getHost();
				return blockLists.stream().noneMatch(p -> p.test(hostName));
			}
		};
	}
}
