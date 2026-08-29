# Yuan Live2D

独立 Forge 客户端模组,提供开箱即用的 Live2D 渲染与配置界面。支持 Minecraft `1.20.1`、Forge `47.4.20`、Java 17。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-FF9900)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.20-00A7E1)]()
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Status](https://img.shields.io/badge/状态-半成品%20·%20存在已知问题-orange)]()

> ⚠️ **半成品实验性模组**:本项目尚未完成,存在较多已知与未知问题(物理渲染、摆动等,详见下文 [现状与已知问题](#现状与已知问题))。适合尝鲜体验,不建议作为生产级依赖;遇到问题欢迎提交 Issue。

## 安装

1. 构建或获取 `build/libs/yuan_live2d-1.0.0.jar`。
2. 把 JAR 复制到游戏实例的 `mods` 目录，例如：

```powershell
Copy-Item -LiteralPath "<repo>\build\libs\yuan_live2d-1.0.0.jar" `
  -Destination "<游戏实例>\mods\yuan_live2d-1.0.0.jar" -Force
```

3. 启动时会从 JAR 刷新 runtime 与默认 Haru 模型到 `config/yuan_live2d/`，无需额外安装 DLL 或模型；用户模型与配置不会被覆盖。

本模组不依赖 Yuan，可以单独安装；也可以与已移除 Live2D 的 `yuan-forge-1.0.0.jar` 同时安装。

## 使用

- `L` 键：打开 Live2D 配置界面。
- `/live2d`：客户端命令，打开同一个配置界面。
- 进入世界后无需按任何键，默认 Haru 模型会自动渲染。
- 模型覆盖：配置界面选中模型后点“覆盖”，可为该模型单独设置启用/可见性/位置/偏移/缩放/透明度/隐藏延迟；未设置的字段继承全局。
- 纹理提示：模型列表显示每个模型的估算纹理占用（`≈MiB`），超过纹理内存预算会红色标记“超预算”；加载失败时预览显示具体原因。
- 世界内调整：配置界面选中模型后点“调整”，进入全屏窗口编辑器，可在世界实景上拖动位置、四边/四角缩放（带吸附辅助与快捷键），底部“写入目标 / 返回 / 应用 / 保存”草稿制编辑。
- 动作互动：模型头部/视线跟随鼠标；随机待机动作与表情（无动作文件的模型自动走程序化小动作）；点击模型按概率触发反应。
- 物理与渲染：physics3.json 摆动幅度可调；轻量物理弹跳/躲闪/边缘挤压；柔和投影与模型切换淡入。
- 以上均可在配置界面的“互动 / 物理 / 渲染”区块调整开关与强度/频率/概率。

## ⚠️ 现状与已知问题

本模组定位为**半成品**,距离稳定仍有明显差距。以下为已知问题(不限于此):

- **物理渲染不完善**:physics3 摆动、轻量物理(弹跳/躲闪/边缘挤压)、柔和投影等效果在部分模型与显卡驱动下表现不稳定,可能出现摆动幅度异常、穿模、闪烁或卡顿
- **摆动(sway)异常**:部分模型/动作下摆动节奏与幅度与配置预期不符
- **渲染稳定性**:模型切换、淡入淡出、纹理预算提示等偶有异常;特定驱动(尤其 AMD)存在渲染崩溃/花屏风险(开发期已有相关崩溃记录)
- **性能**:高开销物理与多模型场景下帧率波动明显
- 其它尚未枚举的 bug

> 由于处于半成品状态,配置项与行为后续可能发生**破坏性变更**;使用前请备份 `config/yuan_live2d/`。

## 数据目录

`config/yuan_live2d/` 存放所有运行数据：

```text
config/yuan_live2d/
  config.json
  models/
  runtime/windows-x86_64/
```

- `config.json`：v2 配置，包含 global / hud / performance / modelOverrides。
- `models/`：模型目录，包含内置 Haru 与用户导入模型。
- `runtime/windows-x86_64/`：首次启动从 JAR 解压的 DLL、FrameworkShaders 与第三方许可文件。

## 旧 Yuan 数据迁移

如果游戏实例中已有旧目录 `config/yuan/live2d/`，首次运行本模组会执行只读迁移：

- 旧 `config.json` 先备份为 `config/yuan_live2d/config.legacy-<epoch>.json`。
- 新目录没有 `config.json` 时，复制旧配置；已有新配置则不覆盖。
- 旧 `models/` 复制到新目录，跳过已存在同名文件。
- 不复制旧 runtime；新模组使用 JAR 内置 runtime。
- 旧可见性值 `ANY_YUAN_ITEM`、`YUAN_ARMOR`、`FULL_YUAN_ARMOR` 自动映射为 `ALWAYS`。

## 构建

```powershell
Set-Location <repo>
.\gradlew.bat build --console=plain
```

构建产物为 `build/libs/yuan_live2d-1.0.0.jar`。构建链包含 Java 断言检查、native smoke、runtime/Haru 打包与 JAR 内容校验。

## 与 Yuan 的关系

- Live2D 已从 Yuan 拆出；Yuan 的 `main` 分支不再包含 Live2D 源码、native、构建任务、L 键或 `/live2d` 命令。
- 两个 JAR 同时安装时，L 键与 `/live2d` 仅由本模组注册，不会重复。

## 许可与第三方内容

- 本模组代码：MIT，见 [LICENSE](LICENSE)。
- 模组内置的 **Live2D Cubism SDK runtime**（`Live2DCubismCore.dll`）与 **Haru 官方样例模型**是 Live2D Inc. 的专有资产，**不适用 MIT 许可**；分发与使用须遵守 Live2D 的授权条款，详见 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。
- 私有/用户自建模型不属于本仓库，运行期从游戏实例的 `config/yuan_live2d/models/` 加载。
