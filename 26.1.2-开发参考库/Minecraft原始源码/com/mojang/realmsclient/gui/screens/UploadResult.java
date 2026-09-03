package com.mojang.realmsclient.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record UploadResult(int statusCode, @Nullable String errorMessage) {
	@Nullable
	public String getSimplifiedErrorMessage() {
		if (this.statusCode >= 200 && this.statusCode < 300) {
			return null;
		} else {
			return this.statusCode == 400 && this.errorMessage != null ? this.errorMessage : String.valueOf(this.statusCode);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private int statusCode = -1;
		@Nullable
		private String errorMessage;

		public UploadResult.Builder withStatusCode(final int statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		public UploadResult.Builder withErrorMessage(@Nullable final String errorMessage) {
			this.errorMessage = errorMessage;
			return this;
		}

		public UploadResult build() {
			return new UploadResult(this.statusCode, this.errorMessage);
		}
	}
}
