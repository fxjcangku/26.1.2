package net.minecraft.server.jsonrpc;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import org.jspecify.annotations.Nullable;

public interface OutgoingRpcMethod<Params, Result> {
	String NOTIFICATION_PREFIX = "notification/";

	MethodInfo<Params, Result> info();

	OutgoingRpcMethod.Attributes attributes();

	@Nullable
	default JsonElement encodeParams(final Params params) {
		return null;
	}

	@Nullable
	default Result decodeResult(final JsonElement result) {
		return null;
	}

	static OutgoingRpcMethod.OutgoingRpcMethodBuilder<Void, Void> notification() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.ParmeterlessNotification::new);
	}

	static <Params> OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Void> notificationWithParams() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.Notification::new);
	}

	static <Result> OutgoingRpcMethod.OutgoingRpcMethodBuilder<Void, Result> request() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.ParameterlessMethod::new);
	}

	static <Params, Result> OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> requestWithParams() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.Method::new);
	}

	public record Attributes(boolean discoverable) {
	}

	@FunctionalInterface
	public interface Factory<Params, Result> {
		OutgoingRpcMethod<Params, Result> create(MethodInfo<Params, Result> info, OutgoingRpcMethod.Attributes attributes);
	}

	public record Method<Params, Result>(MethodInfo<Params, Result> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Params, Result> {
		@Nullable
		@Override
		public JsonElement encodeParams(final Params params) {
			if (this.info.params().isEmpty()) {
				throw new IllegalStateException("Method defined as having no parameters");
			} else {
				return ((ParamInfo)this.info.params().get()).schema().codec().encodeStart(JsonOps.INSTANCE, params).getOrThrow();
			}
		}

		@Override
		public Result decodeResult(final JsonElement result) {
			if (this.info.result().isEmpty()) {
				throw new IllegalStateException("Method defined as having no result");
			} else {
				return (Result)((ResultInfo)this.info.result().get()).schema().codec().parse(JsonOps.INSTANCE, result).getOrThrow();
			}
		}
	}

	public record Notification<Params>(MethodInfo<Params, Void> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Params, Void> {
		@Nullable
		@Override
		public JsonElement encodeParams(final Params params) {
			if (this.info.params().isEmpty()) {
				throw new IllegalStateException("Method defined as having no parameters");
			} else {
				return ((ParamInfo)this.info.params().get()).schema().codec().encodeStart(JsonOps.INSTANCE, params).getOrThrow();
			}
		}
	}

	public static class OutgoingRpcMethodBuilder<Params, Result> {
		public static final OutgoingRpcMethod.Attributes DEFAULT_ATTRIBUTES = new OutgoingRpcMethod.Attributes(true);
		private final OutgoingRpcMethod.Factory<Params, Result> method;
		private String description = "";
		@Nullable
		private ParamInfo<Params> paramInfo;
		@Nullable
		private ResultInfo<Result> resultInfo;

		public OutgoingRpcMethodBuilder(final OutgoingRpcMethod.Factory<Params, Result> method) {
			this.method = method;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> description(final String description) {
			this.description = description;
			return this;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> response(final String resultName, final Schema<Result> resultSchema) {
			this.resultInfo = new ResultInfo<>(resultName, resultSchema);
			return this;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> param(final String paramName, final Schema<Params> paramSchema) {
			this.paramInfo = new ParamInfo<>(paramName, paramSchema);
			return this;
		}

		private OutgoingRpcMethod<Params, Result> build() {
			MethodInfo<Params, Result> methodInfo = new MethodInfo<>(this.description, this.paramInfo, this.resultInfo);
			return this.method.create(methodInfo, DEFAULT_ATTRIBUTES);
		}

		public Holder.Reference<OutgoingRpcMethod<Params, Result>> register(final String key) {
			return this.register(Identifier.withDefaultNamespace("notification/" + key));
		}

		private Holder.Reference<OutgoingRpcMethod<Params, Result>> register(final Identifier id) {
			return Registry.registerForHolder(BuiltInRegistries.OUTGOING_RPC_METHOD, id, this.build());
		}
	}

	public record ParameterlessMethod<Result>(MethodInfo<Void, Result> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Void, Result> {
		@Override
		public Result decodeResult(final JsonElement result) {
			if (this.info.result().isEmpty()) {
				throw new IllegalStateException("Method defined as having no result");
			} else {
				return (Result)((ResultInfo)this.info.result().get()).schema().codec().parse(JsonOps.INSTANCE, result).getOrThrow();
			}
		}
	}

	public record ParmeterlessNotification(MethodInfo<Void, Void> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Void, Void> {
	}
}
