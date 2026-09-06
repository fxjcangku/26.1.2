package net.minecraft.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.PacketListener;
import org.jspecify.annotations.Nullable;

public interface BundlerInfo {
	int BUNDLE_SIZE_LIMIT = 4096;

	static <T extends PacketListener, P extends BundlePacket<? super T>> BundlerInfo createForPacket(
		final PacketType<P> bundlePacketType, final Function<Iterable<Packet<? super T>>, P> constructor, final BundleDelimiterPacket<? super T> delimiterPacket
	) {
		return new BundlerInfo() {
			@Override
			public void unbundlePacket(final Packet<?> packet, final Consumer<Packet<?>> output) {
				if (packet.type() == bundlePacketType) {
					P bundlerPacket = (P)packet;
					output.accept(delimiterPacket);
					bundlerPacket.subPackets().forEach(output);
					output.accept(delimiterPacket);
				} else {
					output.accept(packet);
				}
			}

			@Nullable
			@Override
			public BundlerInfo.Bundler startPacketBundling(final Packet<?> packet) {
				return packet == delimiterPacket ? new BundlerInfo.Bundler() {
					private final List<Packet<? super T>> bundlePackets;

					{
						Objects.requireNonNull(<VAR_NAMELESS_ENCLOSURE>);
						this.bundlePackets = new ArrayList();
					}

					@Nullable
					@Override
					public Packet<?> addPacket(final Packet<?> packet) {
						if (packet == delimiterPacket) {
							return (Packet<?>)constructor.apply(this.bundlePackets);
						} else if (this.bundlePackets.size() >= 4096) {
							throw new IllegalStateException("Too many packets in a bundle");
						} else {
							this.bundlePackets.add(packet);
							return null;
						}
					}
				} : null;
			}
		};
	}

	void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> output);

	@Nullable
	BundlerInfo.Bundler startPacketBundling(Packet<?> packet);

	public interface Bundler {
		@Nullable
		Packet<?> addPacket(Packet<?> packet);
	}
}
