package net.minecraft.commands;

import java.util.Map;
import net.minecraft.network.chat.PlayerChatMessage;
import org.jspecify.annotations.Nullable;

public interface CommandSigningContext {
	CommandSigningContext ANONYMOUS = new CommandSigningContext() {
		@Nullable
		@Override
		public PlayerChatMessage getArgument(final String name) {
			return null;
		}
	};

	@Nullable
	PlayerChatMessage getArgument(String name);

	public record SignedArguments(Map<String, PlayerChatMessage> arguments) implements CommandSigningContext {
		@Nullable
		@Override
		public PlayerChatMessage getArgument(final String name) {
			return (PlayerChatMessage)this.arguments.get(name);
		}
	}
}
