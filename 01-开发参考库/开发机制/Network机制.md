# Network 机制

> 网络机制在 Meteor 里分三层：`Http`（基于 JDK `java.net.http.HttpClient` 的高层 HTTP 请求封装）、`MeteorExecutor`（后台线程池）、`PacketUtils`（Minecraft 协议数据包注册表）。它们互不依赖，addon 做后端上报、远程配置拉取、按协议包名找包类型时分别走这三个入口。

## 概述

- `Http`（`meteordevelopment.meteorclient.utils.network.Http`）：把 `HttpClient.send` 封装成 `Http.get(url)` / `Http.post(url)` → 链式 `Request` 的流式 API，内置 Gson 反序列化。**同步阻塞**调用（配合外部线程池使用）。
- `MeteorExecutor`（同包）：`@PreInit` 创建 `Executors.newCachedThreadPool()` 守护线程池，`execute(Runnable)` 提交异步任务。
- `PacketUtils`（同包）：静态初始化时扫描 `StatusProtocols` / `LoginProtocols` / `ConfigurationProtocols` / `GameProtocols`（以及 `HandshakeProtocols`）的 `CLIENTBOUND_TEMPLATE` / `SERVERBOUND_TEMPLATE`，构建 `Identifier → PacketType` 映射。

---

## 1. Http 请求模型（Request 链式 API）

状态码常量（真实名）：

```java
public static final int SUCCESS = 200;
public static final int BAD_REQUEST = 400;
public static final int UNAUTHORIZED = 401;
public static final int FORBIDDEN = 403;
public static final int NOT_FOUND = 404;
```

底层客户端使用**虚拟线程**执行器：

```java
private static final HttpClient CLIENT = HttpClient.newBuilder()
    .executor(Executors.newVirtualThreadPerTaskExecutor())
    .build();
```

Gson 实例注册了 `Date` 反序列化适配器：

```java
private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(Date.class, new JsonDateDeserializer())
    .create();
```

内部枚举 `Method { GET, POST }`，对外只有两个工厂：

```java
public static Request get(String url)   // new Request(Method.GET, url)
public static Request post(String url)  // new Request(Method.POST, url)
```

`Request` 构造时默认写死 `User-Agent` 头为 `"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ... Chrome/112.0.0.0 Safari/537.36"`。

### Request 链式配置方法（真实签名）

| 方法 | 作用 |
|---|---|
| `header(String name, String value)` | 追加任意请求头 |
| `bearer(String token)` | 加 `Authorization: Bearer <token>` 头 |
| `bodyString(String)` | `Content-Type: text/plain` + 字符串体 |
| `bodyForm(String)` | `Content-Type: application/x-www-form-urlencoded` + 字符串体 |
| `bodyJson(String)` | `Content-Type: application/json` + 原始 JSON 字符串体 |
| `bodyJson(Object)` | 用 GSON `toJson` 序列化对象后作为 body |
| `ignoreExceptions()` | 把异常处理置空（吞掉异常） |
| `exceptionHandler(Consumer<Exception>)` | 自定义异常处理器，默认 `Exception::printStackTrace` |

注意 `body*` 系列**调用后会把内部 `method` 置 null**（避免后续再重复设置 `noBody`）。

### 发送方法（真实签名）

核心私有方法 `_sendResponse(accept, BodyHandler)`：先 `builder.header("Accept", accept)`，若 `method != null` 则 `builder.method(method.name(), BodyPublishers.noBody())`，再 `CLIENT.send(request, responseBodyHandler)`；捕获 `IOException | InterruptedException` 时调用 `exceptionHandler` 并返回 `FailedHttpResponse`（防止返回 null）。

私有 `_send(accept, BodyHandler)`：包装 `_sendResponse`，`statusCode() == 200 ? body() : null`（非 200 一律返回 null）。

对外发送方法：

```java
public void send()                                   // */* + discarding，忽略响应
public HttpResponse<Void> sendResponse()              // */* + discarding，返回完整响应
public InputStream sendInputStream()                  // */* + ofInputStream，仅 200 返回流
public HttpResponse<InputStream> sendInputStreamResponse()
public String sendString()                            // */* + ofString，仅 200 返回字符串
public HttpResponse<String> sendStringResponse()
public Stream<String> sendLines()                     // */* + ofLines
public HttpResponse<Stream<String>> sendLinesResponse()
public <T> T sendJson(Type type)                      // application/json + JsonBodyHandler，仅 200
public <T> HttpResponse<T> sendJsonResponse(Type type)
```

`sendJson*` 走 `JsonBodyHandler.ofJson(GSON, type)`（`meteordevelopment.meteorclient.utils.network.JsonBodyHandler`，一个 `record` 实现 `HttpResponse.BodySubscriber<T>`，在 `getBody()` 完成后 `gson.fromJson(new InputStreamReader(in), type)`）。

### 失败响应兜底

`FailedHttpResponse`（`record FailedHttpResponse<T>(HttpRequest request, Exception exception)` 实现 `HttpResponse<T>`）：`statusCode()` 返回 `Http.BAD_REQUEST`(400)，`body()` 返回 null，`headers()` 返回空表，`version()` 取 `request.version()`。目的：连接中断 / IO 错误时不至于让调用方拿到 null。

## 2. MeteorExecutor 线程池

```java
public class MeteorExecutor {
    public static ExecutorService executor;

    @PreInit
    public static void init() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);                       // 守护线程
            thread.setName("Meteor-Executor-" + threadNumber.getAndIncrement());
            return thread;
        });
    }

    public static void execute(Runnable task) { executor.execute(task); }
}
```

- `@PreInit`（`meteordevelopment.meteorclient.utils.PreInit`）保证它在 `MeteorClient.onInitializeClient` 的 `ReflectInit.init(PreInit.class)` 阶段被反射调用（见「Module注册机制」/「Addon系统」文档的初始化顺序）。
- **无界缓存线程池 + 守护线程**：线程随需创建、空闲 60s 回收，进程结束不阻塞退出。适合后台轮询（心跳、远程配置拉取、崩溃上报）。

## 3. PacketUtils —— 协议包注册表

静态块扫描各协议的 `ProtocolInfo.DetailsProvider`，把包 `Identifier` 映射到 `PacketType`：

```java
Stream.of(StatusProtocols.CLIENTBOUND_TEMPLATE, LoginProtocols.CLIENTBOUND_TEMPLATE,
        ConfigurationProtocols.CLIENTBOUND_TEMPLATE, GameProtocols.CLIENTBOUND_TEMPLATE)
    .map(ProtocolInfo.DetailsProvider::details)
    .forEach(details -> details.listPackets((type, _) -> clientbound.put(type.id(), type)));

Stream.of(HandshakeProtocols.SERVERBOUND_TEMPLATE, StatusProtocols.SERVERBOUND_TEMPLATE,
        LoginProtocols.SERVERBOUND_TEMPLATE, ConfigurationProtocols.SERVERBOUND_TEMPLATE,
        GameProtocols.SERVERBOUND_TEMPLATE)
    .map(ProtocolInfo.DetailsProvider::details)
    .forEach(details -> details.listPackets((type, _) -> serverbound.put(type.id(), type)));
```

对外查询 API（真实签名）：

```java
public static Set<PacketType<?>> getPackets()          // 客户端+服务端并集
public static Set<PacketType<?>> getClientboundPackets()
public static Set<PacketType<?>> getServerboundPackets()
public static PacketType<?> getClientboundPacket(Identifier id)
public static PacketType<?> getServerboundPacket(Identifier id)
public static PacketType<?> getPacket(Identifier id)   // 先查 clientbound 再查 serverbound
public static PacketType<?> getPacket(String name)     // 支持 "clientbound/xxx" "serverbound/xxx" 前缀 + 旧名映射
```

`getPacket(String)` 额外维护一张 `LEGACY_PACKET_MAPPINGS`（`Map<String, PacketType<?>>`，注释明确「**只更新 value，不新增 key**」），把历史包名（如 `"ServerboundMovePlayerPacket.Pos"`、`"ClientboundAddEntityPacket"`）映射到 26.1.2 的 `GamePacketTypes`/`CommonPacketTypes`/`ConfigurationPacketTypes` 等新常量。这是 Meteor 26.1.2 里「面向旧包名找新包类型」的兼容层。

## 运行链路

1. `MeteorClient.onInitializeClient` → `ReflectInit.init(PreInit.class)` 反射扫描各 addon 包并调用 `MeteorExecutor.init()`（线程池就绪）。
2. addon 后台任务在模块/服务里调 `MeteorExecutor.execute(() -> { ... })` 提交异步逻辑。
3. 逻辑内用 `Http.get(STATS_API_URL).header(...).bodyJson(obj).sendJson(Type)` 做上报/拉取。
4. 需要按协议包名过滤/拦截时，用 `PacketUtils.getPacket("ServerboundMovePlayerPacket.PosRot")` 拿到 `PacketType` 与拦截到的事件 `PacketEvent.Send` 里的 `packet.type()` 比对。

## Addon 用法 / 介入点

```java
// 1. 异步 HTTP GET（返回字符串，仅 200 非 null）
MeteorExecutor.execute(() -> {
    String body = Http.get("https://api.example.com/data")
        .bearer(TOKEN)
        .sendString();
});

// 2. 同步 POST JSON + 反序列化为类型 T（在后台线程里用）
T resp = Http.post("https://api.example.com/report")
    .header("X-Api-Key", key)
    .bodyJson(new Report(...))
    .sendJson(MyType);

// 3. 静默失败（吞异常，避免刷屏）
String s = Http.get(url).ignoreExceptions().sendString();

// 4. 按包名找 PacketType（写 PacketCanceller 风格模块时）
var t = PacketUtils.getPacket("ServerboundMovePlayerPacket.Pos");
```

配套类：`Capes` / `OnlinePlayers`（同 `utils/network/` 包）也是基于 `Http` / `MeteorExecutor` 的网络用法示例。

## 常见坑

1. **`sendString/sendJson/...` 只在 `200` 返回体，其它状态码一律 `null`**。要拿到 4xx/5xx 的完整响应必须用带 `Response` 后缀的版本（`sendStringResponse()` 等）并自行判断 `statusCode()`。
2. **请求是同步阻塞的**：`Http` 自身没有异步，直接在主线程调用会卡客户端；务必包在 `MeteorExecutor.execute` 里（或自建线程）。
3. **`sendJson(Type)` 依赖 Gson 的 `Date` 适配器**：目标类型里含 `java.util.Date` 字段才走 `JsonDateDeserializer`，自定义类型需保证能被 Gson 序列化。
4. **虚拟线程执行器**：`Http.CLIENT` 用 `newVirtualThreadPerTaskExecutor`，不占平台线程，但仍要控制请求频率（addon 心跳/统计轮询应注意节流）。
5. **`PacketUtils.getPacket(String)` 前缀**：写 `"clientbound/..."` / `"serverbound/..."` 时前缀后的内容是包 `Identifier`（形如 `minecraft:add_entity`），前缀长度固定为 12。
6. **`MeteorExecutor.execute` 无返回值**：需要结果时用 `Future`/`CompletableFuture` 自行编排，或把回调写进 `Runnable`。

## 源码依据

- `meteordevelopment/meteorclient/utils/network/Http.java` —— 状态码常量、HttpClient 虚拟线程、Gson+Date 适配、Request 链式 API、全部 send* 方法、`get`/`post` 工厂。
- `meteordevelopment/meteorclient/utils/network/MeteorExecutor.java` —— `@PreInit` 线程池初始化、`execute`。
- `meteordevelopment/meteorclient/utils/network/FailedHttpResponse.java` —— 失败响应兜底（statusCode 400 / body null）。
- `meteordevelopment/meteorclient/utils/network/JsonBodyHandler.java` —— Gson BodySubscriber 实现 `ofJson`。
- `meteordevelopment/meteorclient/utils/network/PacketUtils.java` —— 协议包注册表、`getPacket`/`getClientboundPackets`/旧包名映射。
- `meteordevelopment/meteorclient/utils/network/Capes.java` / `OnlinePlayers.java` —— 网络请求真实用法示例。
- `meteordevelopment/meteorclient/utils/PreInit.java` —— `@PreInit` 标注（初始化时机依据）。