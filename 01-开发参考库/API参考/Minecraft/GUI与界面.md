# GUI 与界面

> 源码依据：Minecraft原始源码/net/minecraft/client/gui/... 26.1.2 Mojang官方映射

> ⚠️ 26.1.2 GUI 渲染架构已大改：旧的 `Screen#render(GuiGraphics, ...)`、`GuiGraphics`、`renderSlot/renderBg/renderLabels` 命名在本版本**已不存在**。渲染改为「抽帧（render state extraction）」模型，绘图上下文类更名为 `GuiGraphicsExtractor`。开发 addon 时务必按新模型编写。

## 一、Screen（net.minecraft.client.gui.screens.Screen）

### 类信息
- 全限定名：`net.minecraft.client.gui.screens.Screen`
- 包名：`net.minecraft.client.gui.screens`
- 源码路径：`net/minecraft/client/gui/screens/Screen.java`
- 继承与接口：
  ```java
  public abstract class Screen extends AbstractContainerEventHandler implements Renderable
  ```
- `Renderable` 接口（26.1.2）：
  ```java
  public interface Renderable {
      void extractRenderState(final GuiGraphicsExtractor graphics, int mouseX, int mouseY, final float a);
  }
  ```

### 核心字段
| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `minecraft` | `Minecraft`（`protected final`） | 游戏实例 |
| `font` | `Font`（`protected final`） | 字体渲染器 |
| `title` | `Component`（`protected final`） | 标题 |
| `width` | `int`（`public`） | 屏幕宽 |
| `height` | `int`（`public`） | 屏幕高 |
| `children` | `List<GuiEventListener>`（`private`） | 事件监听子元素 |
| `renderables` | `List<Renderable>`（`private`） | 渲染子元素 |
| `narratables` | `List<NarratableEntry>`（`private`） | 旁白条目 |
| `screenExecutor` | `Executor`（`protected final`） | 屏幕更新执行器 |

### 生命周期与核心方法（真实签名）
```java
protected void init();           // 首帧（或 init(width,height) 后才调用）初始化布局
public void tick();              // 每帧逻辑
public void removed();           // 屏幕被切换时
public void added();             // 屏幕被设置时
public void onClose();           // 请求关闭（默认 setScreen(null)）
public void resize(int width, int height);  // 窗口尺寸变化
protected void rebuildWidgets(); // 重新构建控件（默认空，子类覆盖）
protected void repositionElements(); // 尺寸变化时重排（默认调用 rebuildWidgets）
public boolean isPauseScreen();  // 是否暂停游戏（默认 true）
public boolean isInGameUi();     // 是否游戏内覆盖 UI（默认 false）
protected void init(); // 见上
// —— 渲染（新模型）——
public final void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a); // 渲染主入口（覆盖点）
public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);   // 背景
protected void extractBlurredBackground(GuiGraphicsExtractor graphics);   // 背景模糊
protected void extractPanorama(GuiGraphicsExtractor graphics, float a);   // 全景背景
protected void extractMenuBackground(GuiGraphicsExtractor graphics);      // 菜单背景
public void extractTransparentBackground(GuiGraphicsExtractor graphics);  // 游戏内透明背景（渐变遮罩）
```
- **关键点**：addon 覆盖 Screen 时，绘制逻辑写进 `extractRenderState(GuiGraphicsExtractor, int, int, float)`，而不是旧版的 `render(...)`。
- 输入事件（26.1.2 真实签名）：`public boolean keyPressed(final KeyEvent event)`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`charTyped` 等，参数已改为 `KeyEvent`/`MouseButtonEvent` 等事件对象。

### 控件管理
```java
protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);
protected <T extends Renderable> T addRenderableOnly(T renderable);   // 只渲染不监听
protected void clearWidgets();
```

## 二、GuiGraphicsExtractor（26.1.2 绘图上下文，旧 GuiGraphics 已更名）

- 全限定名：`net.minecraft.client.gui.GuiGraphicsExtractor`
- 源码路径：`net/minecraft/client/gui/GuiGraphicsExtractor.java`
- **旧名 `GuiGraphics` 在 26.1.2 已不存在**（Yarn/旧版旧名，标注备查）。

### 关键方法（真实签名，节选）
```java
public void nextStratum();                              // 切到下一渲染层
public void extractDeferredElements(int mouseX, int mouseY, float a); // 抽出延迟元素
public void blurBeforeThisStratum();                    // 对该层做背景模糊
public void blit(RenderPipeline, Identifier, ...);      // 贴图（多个重载）
public void blitSprite(RenderPipeline, Identifier, int x, int y, int w, int h); // 精灵图
public void blitSprite(RenderPipeline, TextureAtlasSprite, int x, int y, int w, int h);
public void fill(int x0, int y0, int x1, int y1, int col);                     // 纯色矩形
public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col);
public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2);  // 渐变
```
- 参数 `RenderPipeline` 常为 `RenderPipelines.GUI_TEXTURED`（来自 `net.minecraft.client.renderer.RenderPipelines`）。
- 文本绘制不再走 `GuiGraphicsExtractor.drawString`，改用 `Font#drawInBatch(...)`（见下）。

### Font 文本绘制（net.minecraft.client.gui.Font）
```java
public int width(String str);
public int width(FormattedText text);
public int width(FormattedCharSequence text);
public void drawInBatch(Component str, float x, float y, int color, boolean dropShadow,
        Matrix4fc pose, MultiBufferSource bufferSource, Font.DisplayMode displayMode,
        int backgroundColor, int packedLightCoords);
public void drawInBatch(FormattedCharSequence str, ...); // 重载
public void drawInBatch8xOutline(...); // 8 倍描边
```

## 三、AbstractContainerScreen（容器 UI 核心）

- 全限定名：`net.minecraft.client.gui.screens.inventory.AbstractContainerScreen`
- 泛型：`public abstract class AbstractContainerScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T>`
- 源码路径：`net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java`

### 关键字段（真实类型）
| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `menu` | `T`（`protected final`） | 容器菜单 |
| `leftPos` | `int`（`protected`） | GUI 左上角 X（= `(width - imageWidth) / 2`） |
| `topPos` | `int`（`protected`） | GUI 左上角 Y（= `(height - imageHeight) / 2`） |
| `imageWidth` | `int`（`protected final`） | GUI 宽（默认 176） |
| `imageHeight` | `int`（`protected final`） | GUI 高（默认 166） |
| `titleLabelX/titleLabelY` | `int` | 标题文本位置 |
| `inventoryLabelX/inventoryLabelY` | `int` | 玩家背包标签位置 |
| `hoveredSlot` | `Slot`（`@Nullable protected`） | 当前悬停插槽 |
| `clickedSlot`/`quickdropSlot` | `Slot` | 点击/快捷丢弃槽 |
| `draggingItem` | `ItemStack` | 拖动中的物品 |
| `playerInventoryTitle` | `Component`（`protected final`） | 背包标题 |

### 插槽定位与渲染方法（真实签名，替代旧 renderSlot）
```java
protected void init();  // 计算 leftPos/topPos
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a); // 平移 + 标签 + 插槽
protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY); // 单个插槽（旧 renderSlot）
protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY);           // 遍历所有槽
protected void extractFloatingItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, @Nullable String count);
public void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY);        // 手上物品
protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY);          // 标题/标签文本
protected void extractSlotHighlightBack/Front(...); // 悬停高亮前后层
protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY);
private Slot getHoveredSlot(double mouseX, double mouseY);
```
- 插槽坐标约定：`leftPos + slot.x` / `topPos + slot.y`（`extractSlot` 中 `int x = slot.x; int y = slot.y;`，外部已做 pose 平移）。
- 容器背景纹理常量：`INVENTORY_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/inventory.png")`，`BACKGROUND_TEXTURE_WIDTH/HEIGHT = 256`，`DEFAULT_IMAGE_WIDTH = 176`，`DEFAULT_IMAGE_HEIGHT = 166`。

## 四、ContainerScreen 及其子类

- `net.minecraft.client.gui.screens.inventory.ContainerScreen`：`public class ContainerScreen extends AbstractContainerScreen<ChestMenu>`（箱子/3x9 容器）。
- 子类清单（`screens/inventory` 下真实存在，节选）：
  `CraftingScreen`、`InventoryScreen`、`CreativeModeInventoryScreen`、`AnvilScreen`、`EnchantmentScreen`、`BrewingStandScreen`、`FurnaceScreen`、`MerchantScreen`、`BeaconScreen`、`StonecutterScreen`、`SmithingScreen`、`HopperScreen`、`DispenserScreen`、`ShulkerBoxScreen`、`CrafterScreen`、`LoomScreen` 等。
- `MenuAccess<T>`（`screens/inventory/MenuAccess.java`）：容器 Screen 的数据访问接口。

## 五、Button / AbstractWidget

### AbstractWidget（net.minecraft.client.gui.components.AbstractWidget）
- 继承链：`AbstractWidget extends AbstractContainerWidget implements Widget, GuiEventListener, NarratableEntry`（以源码为准）。
- 关键字段（真实）：
  | 字段 | 类型 | 用途 |
  | --- | --- | --- |
  | `width` | `int`（`protected`） | 宽 |
  | `height` | `int`（`protected`） | 高 |
  | `active` | `boolean`（`public`） | 是否可用 |
  | `visible` | `boolean`（`public`） | 是否可见 |
- 关键方法（真实签名）：
  ```java
  public int getX();           public void setX(int x);
  public int getY();           public void setY(int y);
  public int getWidth();       public void setWidth(int width);
  public int getHeight();      public void setHeight(int height);
  public Component getMessage(); public void setMessage(Component message);
  public boolean isHovered();   public boolean isHoveredOrFocused();
  public boolean isFocused();   public boolean isActive();  // visible && active
  public void onClick(MouseButtonEvent event, boolean doubleClick); // 覆盖点
  public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a); // final，先判 visible
  ```

### Button（net.minecraft.client.gui.components.Button）
```java
public class Button extends AbstractWidget {  // 简洁形态
    protected final Button.OnPress onPress;
    public static Button.Builder builder(Component message, Button.OnPress onPress);
    public void onPress(InputWithModifiers input);
    @FunctionalInterface public interface OnPress { void onPress(Button button); }
    public static class Builder {   // 链式构建
        public Button.Builder pos(int x, int y);
        public Button.Builder width(int width);
        public Button.Builder size(int width, int height);
        public Button.Builder bounds(int x, int y, int width, int height);
        public Button.Builder tooltip(@Nullable Tooltip tooltip);
        public Button build();
    }
}
```
- 典型用法：`this.addRenderableWidget(Button.builder(msg, b -> onPress()).bounds(x, y, w, h).build());`

### 其他组件（真实存在，节选）
- `net.minecraft.client.gui.components.EditBox`（文本框）、`MultiLineEditBox`、`Checkbox`、`CycleButton`、`AbstractSliderButton`、`AbstractSelectionList`、`Tooltip`、`StringWidget`、`ImageButton`、`SpriteIconButton`、`CommandSuggestions`、`ChatComponent` 等。

## 六、组件渲染（以源码为准）

26.1.2 组件渲染走 `Renderable#extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick)`。`Screen` 顶层入口 `extractRenderStateWithTooltipAndSubtitles` 依次 `extractBackground → nextStratum → extractRenderState → extractDeferredElements`。绘制操作（blit/blitSprite/fill/fillGradient）都调用 `GuiGraphicsExtractor`，`RenderPipelines.GUI_TEXTURED` 是 GUI 贴图最常用管线。

## 常见坑

1. **旧 API 已改名**：别再用 `Screen#render(GuiGraphics,...)`、`GuiGraphics`、`renderSlot/renderBg/renderLabels`——26.1.2 分别是 `extractRenderState`、`GuiGraphicsExtractor`、`extractSlot/extractContents/extractLabels`。
2. **`setScreen` 必须在主线程**：`Minecraft#setScreen(Screen)` 会校验当前线程，非游戏线程调用将报错/警告。
3. **`init` 不是每帧调用**：只在屏幕首次打开及 `resize`（经 `repositionElements`）时重建；每帧逻辑放 `tick()`。
4. **字段 `width/height` 首次赋值**：真正的宽高来自 `init(int, int)`，构造器里拿到的是 0，别在构造器里依赖它。
5. **`isPauseScreen()` 默认 true**：自定义覆盖层若不应暂停游戏需返回 false；`isInGameUi()` 默认 false。
6. **控件坐标**：容器插槽坐标基于 `leftPos/topPos` 偏移，不要用绝对屏幕坐标。
7. **`AbstractWidget.extractRenderState` 是 final**：子控件自定义绘制要覆盖其内部的 `renderWidget` 类方法，不能覆盖 `extractRenderState`。
8. **悬停槽判空**：`hoveredSlot` 为 `@Nullable`，读 `hoveredSlot.hasItem()` 前需判空。