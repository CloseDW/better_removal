# libs/ —— 编译期依赖

本工程的 `build.gradle` 用 **compileOnly** 引用第三方模组的 API。两个依赖的处理方式不同：

| 文件 | 来源 | 协议 | 是否随仓库提交 |
|---|---|---|---|
| `configured-neoforge-1.21.1-2.6.3.jar` | Configured（MrCrayfish）— Modrinth / CurseForge / [GitHub Releases](https://github.com/MrCrayfish/Configured/releases/tag/v1.21.1-2.6.3) | **LGPL-3.0**（允许再分发） | **是**① |
| Jade | Jade（Snownee）— 由 Gradle 解析 `maven.modrinth:jade:eYz2YBGT`② | **CC-BY-NC-SA-4.0**（不允许再分发） | **否** |

① 提交它是为了让全新克隆 / GitHub Actions 不需要任何额外步骤就能编译（`.gitignore` 里对
`libs/configured-*.jar` 开了例外）。
② 版本 ID `eYz2YBGT` = Jade **15.10.6+neoforge**（版本号里带 `+`，用 ID 更稳）。

换版本时改 `build.gradle` 里对应那一行；Configured 还要把新 jar 放进本目录一起提交。

两个 jar 都**不会被打进本模组**。运行时的要求不同：

- **Configured**：必需依赖（`neoforge.mods.toml` 里声明 `required`）——没装游戏会直接报缺少依赖；
- **Jade**：可选——不装就只是没有工具提示预览。
