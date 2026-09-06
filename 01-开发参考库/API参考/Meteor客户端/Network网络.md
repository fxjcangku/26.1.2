> 源码依据：Meteor原始源码/meteordevelopment/meteorclient/utils/network/… 与 utils/player/ChatUtils.java；原版承载见 Minecraft原始源码/net/minecraft/network/Connection.java、net/minecraft/client/multiplayer/ClientPacketListener.java　Meteor Client 26.1.2-SNAPSHOT

# Network 网络

Meteor 的网络工具分两层：
1. **HTTP 客户端**：`Http`（`utils/network/Http.java`），基于 JDK `java.net.http.HttpClient` + 虚拟线程，用于拉取 cape、online 上报等外网接口。
2. **原版网络承载**：`Connection` / `ClientPacketListener`（Minecraft 原始源码），承载游戏协议收发。

---

## 1. Http（HTTP 工具）

### 类名 / 源码路径
`Http`／`meteordevelopment/meteorclient/utils/network/Http.java`

### 常量
```java
public static final int SUCCESS = 200;
public static final int BAD_REQUEST = 400;
public static final int UNAUTHORIZED = 401;
public static final int FORBIDDEN = 403;
public static final int NOT_FOUND = 404;
```

### 关键方法（真实签名）
```java
public static Request get(String url)   // 发起 GET
public static Request post(String url)  // 发起 POST
```
> 返回 `Http.Request`（`public static class Request`），全部链式调用。

### Http.Request（内部类）
```java
public Request header(String name, String value)
public Request bearer(String token)                 // Authorization: Bearer ...
public Request bodyString(String string)            // Content-Type: text/plain
public Request bodyForm(String string)              // application/x-www-form-urlencoded
public Request bodyJson(String string)              // application/json
public Request bodyJson(Object object)              // Gson 序列化
public Request ignoreExceptions()
public Request exceptionHandler(Consumer<Exception> exceptionHandler)

public void send()                                  // 忽略响应体
public HttpResponse<Void> sendResponse()
@Nullable public InputStream sendInputStream()
public HttpResponse<InputStream> sendInputStreamResponse()
@Nullable public String sendString()
public HttpResponse<String> sendStringResponse()
@Nullable public Stream<String> sendLines()
public HttpResponse<Stream<String>> sendLinesResponse()
@Nullable public <T> T sendJson(Type type)
public <T> HttpResponse<T> sendJsonResponse(Type type)
```
> 实现细节：底层 `java.net.http.HttpClient`（虚拟线程 per task），`send*` 方法里状态码非 200 时返回 `null`；异常时若指定了 exceptionHandler 则回调，否则 `printStackTrace`。addon 拉取文本典型写法：
> `String body = Http.get("https://...").sendString();`

### addon 使用场景
- 请求外部 API 获取 JSON：`Http.get(url).sendJson(MyType.class)`（需 `Type`）。
- 无响应体上报：`Http.post(url).bodyJson(obj).ignoreExceptions().send();`

---

## 2. 相关网络工具类（真实存在）

### 2.1 MeteorExecutor
`meteordevelopment/meteorclient/utils/network/MeteorExecutor.java`
```java
public static ExecutorService executor;
public static void execute(Runnable task)
```
内部 `Executors.newCachedThreadPool`（守护线程，命名 `Meteor-Executor-N`）。用于把网络/耗时任务丢到后台线程。

### 2.2 OnlinePlayers
`meteordevelopment/meteorclient/utils/network/OnlinePlayers.java`
```java
public static void update()  // 每 5 分钟 Http.post(".../api/online/ping").ignoreExceptions().send()
public static void leave()   // Http.post(".../api/online/leave")
```

### 2.3 Capes
`meteordevelopment/meteorclient/utils/network/Capes.java`
```java
public static Identifier get(Player player)   // 按玩家 UUID 取披风纹理 Identifier
```
内部从 `https://meteorclient.com/api/capeowners` / `.../api/capes` 拉取并下载贴图，`@PreInit` + `MeteorClient.EVENT_BUS.subscribe(Capes.class)` 在 `TickEvent.Post` 里注册纹理。

### 2.4 FailedHttpResponse
`meteordevelopment/meteorclient/utils/network/FailedHttpResponse.java`——实现 `HttpResponse`，当请求抛异常时作为返回给 `send*Response` 的替身。

### 2.5 JsonBodyHandler
`meteordevelopment/meteorclient/utils/network/JsonBodyHandler.java`——`HttpResponse.BodyHandler`，配合 Gson + `Type` 解析 JSON。

### 2.6 ChatUtils
`meteordevelopment.meteorclient.utils.player.ChatUtils`（源码路径 `utils/player/ChatUtils.java`）——聊天输出工具（也是「聊天发包」通道，详见《Packet数据包.md》）：
```java
public static void sendPlayerMsg(String message)
public static void sendPlayerMsg(String message, boolean addToHistory)
public static void info(String message, Object... args)
public static void warning(String message, Object... args)
public static void error(String message, Object... args)
public static void infoPrefix(String prefix, String message, Object... args)
public static void warningPrefix(String prefix, String message, Object... args)
public static void errorPrefix(String prefix, String message, Object... args)
public static void sendMsg(Component message)                      // 以及多个重载
public static Component getMeteorPrefix()
public static void registerCustomPrefix(String packageName, Supplier<Component> supplier)
public static void unregisterCustomPrefix(String packageName)
public static void forceNextPrefixClass(Class<?> klass)
public static MutableComponent formatCoords(Vec3 pos)
```

### 2.7 Discord / RPC
- 26.1.2 的 Discord 相关实现是**模块** `meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence`（DiscordRichPresence）。Meteor `utils/network` 下没有单独的 `RPC`/`DiscordRPC` 工具类；具体字段/方法以 `DiscordPresence.java` 源码为准（待源码确认其内部 RPC 细节）。

---

## 3. 原版网络承载（Minecraft 原始源码）

### 3.1 Connection
`net.minecraft.network.Connection`（路径 `Minecraft原始源码/net/minecraft/network/Connection.java`）
```java
public void send(final Packet<?> packet)
public void send(final Packet<?> packet, @Nullable final ChannelFutureListener listener)
public void send(final Packet<?> packet, @Nullable final ChannelFutureListener listener, final boolean flush)
```
> 这是 `mc.getConnection().send(packet)` 和 `PacketEvent.sendSilently` 最终调用的底层方法。`flush=true` 立即刷网（`sendSilently` 用之）。

### 3.2 ClientPacketListener
`net.minecraft.client.multiplayer.ClientPacketListener`（路径 `Minecraft原始源码/net/minecraft/client/multiplayer/ClientPacketListener.java`）
```java
public class ClientPacketListener extends ClientCommonPacketListenerImpl implements ClientGamePacketListener, TickablePacketListener
public void sendChat(final String content)
public void sendCommand(final String command)
```
> `mc.getConnection()` 返回 `ClientPacketListener`；`mc.player.connection` 也指向它。addon 发聊天/命令即 `mc.player.connection.sendCommand("...")` / `sendChat("...")`。

---

## 常见坑
1. `Http.send*` 系列在非 200 状态下返回 `null`（而不是抛异常），处理响应务必判空。
2. `Http` 请求是**同步阻塞**的（尽管执行器是虚拟线程）；在客户端主线程调用会卡顿——应包进 `MeteorExecutor.execute(...)` 或异步回调。
3. 网络/耗时任务不要直接在主线程 sleep 或阻塞 IO，用 `MeteorExecutor.execute`。
4. `mc.getConnection()` / `mc.player` 未进世界时为 `null`，发包前判空。
5. 没有独立的 `HttpRequest` 顶层类：HTTP 请求对象是 `Http.Request`（内部类），别 import `HttpRequest`。
6. Discord Presence 是模块而非 `utils/network` 下的 RPC 工具，接入 Discord 功能请参考 `systems/modules/misc/DiscordPresence.java` 源码。