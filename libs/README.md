# libs/ —— 本地编译依赖（不进 git）

本工程的 `build.gradle` 用 **compileOnly** 引用这两个文件，**没有就不会编译通过**：

| 需要的文件 | 来源 | 协议 |
|---|---|---|
| `configured-neoforge-1.21.1-2.6.3.jar` | Configured（MrCrayfish）— Modrinth / CurseForge | LGPL-3.0 |
| `jade-neoforge-1.21.1.jar` | Jade（Snownee）— Modrinth / CurseForge | CC-BY-NC-SA-4.0 |

> 两个 jar 都**不要提交到仓库**（见根目录 `.gitignore`）。Jade 的协议明确不允许再分发，
> Configured 的 LGPL 也有附加条件。这里只用它们做编译期 API：**Configured 是必需依赖**（`neoforge.mods.toml` 里声明 required，运行时必须装，否则游戏会报缺少依赖）；**Jade 可选**（不装就没有工具提示预览）。
>
> 文件名/版本要与 `build.gradle` 里的 `compileOnly files("libs/...")` 完全一致；
> 换版本时两处一起改。

**更省事的做法**：把这两行换成 Modrinth Maven 坐标（公共仓库、无需手动放 jar），
就不再需要这个目录了 —— 需要的话可以让脚本改成 `maven.modrinth:jade:<版本>` 形式。
