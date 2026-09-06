package net.minecraft.network.protocol.game;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundSpectateEntityPacket(int entityId) implements Packet<ServerGamePacketListener> {
	public static final StreamCodec<ByteBuf, ServerboundSpectateEntityPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ServerboundSpectateEntityPacket::entityId, ServerboundSpectateEntityPacket::new
	);

	@Override
	public PacketType<ServerboundSpectateEntityPacket> type() {
		return GamePacketTypes.SERVERBOUND_SPECTATE_ENTITY;
	}

	public void handle(final ServerGamePacketListener listener) {
		listener.handleSpectateEntity(this);
	}
}
