package net.minecraft.advancements;

import java.time.Instant;
import net.minecraft.network.FriendlyByteBuf;
import org.jspecify.annotations.Nullable;

public class CriterionProgress {
	@Nullable
	private Instant obtained;

	public CriterionProgress() {
	}

	public CriterionProgress(final Instant obtained) {
		this.obtained = obtained;
	}

	public boolean isDone() {
		return this.obtained != null;
	}

	public void grant() {
		this.obtained = Instant.now();
	}

	public void revoke() {
		this.obtained = null;
	}

	@Nullable
	public Instant getObtained() {
		return this.obtained;
	}

	public String toString() {
		return "CriterionProgress{obtained=" + (this.obtained == null ? "false" : this.obtained) + "}";
	}

	public void serializeToNetwork(final FriendlyByteBuf output) {
		output.writeNullable(this.obtained, FriendlyByteBuf::writeInstant);
	}

	public static CriterionProgress fromNetwork(final FriendlyByteBuf input) {
		CriterionProgress result = new CriterionProgress();
		result.obtained = input.readNullable(FriendlyByteBuf::readInstant);
		return result;
	}
}
