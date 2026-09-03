package net.minecraft.network.protocol;

import net.minecraft.resources.Identifier;

public record PacketType<T extends Packet<?>>(PacketFlow flow, Identifier id) {
	public String toString() {
		return this.flow.id() + "/" + this.id;
	}
}
