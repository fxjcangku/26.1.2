# Reference —— 第三方参考资料

这里放**别人的**项目，只用于查阅 26.1.2 的真实 API 写法，不参与本项目构建。

目录内容不入库（体积大、且是他人代码），只有本文件提交。clone 之后按下面命令重建。

## 为什么需要

26.1.2（内部版本 1.21.11）Mojang 取消混淆并大规模改名，Yarn 映射退役。`Mappings/` 能告诉你某个类是否存在、方法签名长什么样，但回答不了「实际项目怎么组织这段代码」。

JsMacros Reloaded 是已经完成 26.1.2 迁移的真实项目，作者在移植 PR 里逐条记录了改名。拿它和 `Mappings/` 交叉验证，比单看映射文件可靠。

## 当前内容

```
Reference/
├─ README.md                                    本文件，入库
└─ JsMacros-26.1.2/
   ├─ mods/jsmacros-26.1.2-2.0.3-fabric.jar     成品，29.28 MB
   └─ source/                                   v2.0.3 源码浅克隆
```

JsMacros Reloaded 2.0.3，作者 grepsedawk，MPL-2.0，要求 Minecraft 26.1.2 + Fabric。

JAR 的 SHA256 与 GitHub Release 公布的 digest 核对一致：

```
bb8664f2050b070d231653a20e2077fd95abc5a3ab28a8d2f10fe95b8b79fcb9
```

## 重建

```powershell
New-Item -ItemType Directory -Force -Path "Reference\JsMacros-26.1.2\mods"
Invoke-WebRequest `
  -Uri "https://github.com/grepsedawk/JSMacros/releases/download/v2.0.3/jsmacros-26.1.2-2.0.3-fabric.jar" `
  -OutFile "Reference\JsMacros-26.1.2\mods\jsmacros-26.1.2-2.0.3-fabric.jar"

git clone --depth 1 --branch v2.0.3 `
  https://github.com/grepsedawk/JSMacros.git `
  Reference\JsMacros-26.1.2\source
```

## 怎么查

想知道某个 26.1.2 API 实际怎么用，在 `source/src` 里搜。几个高频参考点：

| 想查什么 | 去哪看 |
|---------|--------|
| Mixin 注入点怎么写 | `source/src/client/java/xyz/wagyourtail/jsmacros/client/mixin/` |
| 聊天消息收发 | `mixin/events/MixinChatComponent.java`、`MixinChatListener.java` |
| 玩家操作与移动 | `client/movement/MovementDummy.java`、`MixinLocalPlayer.java` |
| 容器与背包交互 | `mixin/access/MixinAbstractContainerScreen.java` |
| 网络包拦截 | `mixin/events/MixinClientPacketListener.java`、`MixinConnection.java` |
| 注册表访问 | `client/api/classes/RegistryHelper.java` |
| NBT 读写 | `client/api/helper/NBTElementHelper.java` |

注意它的类名从 Yarn 迁到了官方映射，所以文件名是 `MixinLocalPlayer` 而不是 `MixinClientPlayerEntity`——这本身就是改名的例证。

## 边界

- **不复制代码进本项目**。MPL-2.0 是弱传染性许可，复制文件会带上分发义务（保留许可声明、标明修改、提供对应源码）。
- 只看写法和思路，自己实现。
- 也不要嵌套打包进 `yiyiaddon1.1-personal.jar`：会让产物膨胀到 30MB，且和用户已装的独立版 `jsmacros` 撞 mod id 导致启动失败。要联动就用 `fabric.mod.json` 的 `recommends` 加反射调用。

## 新增其他参考项目

同样的结构：`Reference/{项目名}-{版本}/`，源码放 `source/`，成品放 `mods/`，然后在本文件补一条说明和重建命令。
