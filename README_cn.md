# Process Details

[English](README.md) | **中文**

一个面向 **Minecraft 1.21.11** 的客户端 Fabric mod,为 Minecraft 各个"看不见进度"的加载
过程提供详细信息显示。它由原先的 *Loading Progress Details* 和 *Xaero Loading Details*
两个 mod 合并而来,并新增了退出单人世界时的保存进度显示。

## 功能

### 资源重载详情

资源重载期间(启动或 F3+T),在 Mojang 加载界面的进度条下方额外绘制两行信息:

- **第一行** —— 当前阶段(`正在准备资源` / `正在应用资源` / `重载完成`)、与进度条一致的
  百分比、已用时间。
- **第二行** —— 已准备/已应用的任务计数,以及仍在等待的 reloader(例如
  `model_loader`、`sprite_uploader`),让你准确知道慢在哪里。

通过 Fabric API 注册的 reloader 会显示其 Fabric id,而不是混淆后的类名。

### 世界保存详情(新增)

退出单人世界时,在原版"正在保存世界"屏幕下追加:

- **第一行** —— 整体保存百分比与已用时间。
- **第二行** —— 当前正在保存的维度、区块写入进度、维度序号
  (例如 `minecraft:overworld · 区块 123/456 · 维度 1/3`)。

进度数据来自整合服务端的保存路径
(`MinecraftServer#stopServer` → `ServerLevel#save` → `ChunkMap#saveAllChunks`),
因此退出多人服务器(无保存)与后台自动保存时不会显示。

### Xaero's World Map 详情(可选)

安装 [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) 后,其写死的
"Preparing World Map..." 界面会被替换为地图管线的实时详情:最近活跃的处理器线程的阶段
栈(含各阶段耗时),以及处理器的实时状态(世界 id、维度、各暂停标志、区域加载/保存队列、
正在读取的区域)。

未安装 Xaero's World Map 时,该功能被完全跳过(通过 mixin 配置插件禁用相关 mixin),
其余功能不受影响。

本 mod 仅编译时引用 Xaero 的无混淆类(`xaero.map.*`),从不分发其内容。

## 与 RRLS 的兼容性

资源重载详情的设计与 [RRLS (Remove Reloading Screen)](https://modrinth.com/mod/rrls) 兼容:

- 本 mod 注入在 `LoadingOverlay#drawProgressBar` 的末尾,不包装、不重定向、不取消任何
  原版调用,因此与 RRLS 对同一方法的注入(仅重新着色/插值进度条)不冲突。
- 当 RRLS 配置为完全跳过加载界面时,它使用的虚拟 graphics 会让所有绘制调用变成空操作,
  本 mod 的文本会静默消失而不会崩溃。
- 当 RRLS 处于 `PROGRESS` 模式时,详情行也会绘制在 RRLS 自己的进度条下方。

## 构建

先将 Xaero 的 jar(All Rights Reserved —— 不入库)放入 `libs/`:

```
libs/xaeroworldmap-fabric-1.21.11-1.40.16.jar
libs/xaerolib-fabric-1.21.11-1.1.15.jar   (从 worldmap 的 META-INF/jars/ 解出)
```

然后:

```bash
./gradlew build
```

构建产物位于 `build/libs/`。

## 许可证

[MIT](LICENSE)
