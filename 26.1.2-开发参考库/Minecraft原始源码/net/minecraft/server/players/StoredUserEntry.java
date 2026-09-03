package net.minecraft.server.players;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

public abstract class StoredUserEntry<T> {
	@Nullable
	private final T user;

	public StoredUserEntry(@Nullable final T user) {
		this.user = user;
	}

	@Nullable
	public T getUser() {
		return this.user;
	}

	boolean hasExpired() {
		return false;
	}

	protected abstract void serialize(final JsonObject object);
}
