<div align="center">
  <img src="src/main/resources/icons/octopus.svg" alt="OverCode Logo" width="150"/>
  <h1>OverCode 🚀</h1>
</div>

> **"Don't just write code, OverCode it."** —— 深度集成于 IntelliJ IDEA 的下一代 AI 编程助手，赋予开发者完全的 AI 自主权。

<div align="center">

[![Platform](https://img.shields.io/badge/Platform-IntelliJ-blue.svg)](https://plugins.jetbrains.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Open Source](https://img.shields.io/badge/Open%20Source-Yes-brightgreen.svg)](https://github.com/bboygf/over-code)

</div>

---
<!-- Plugin description -->
OverCode is an intelligent **AI programming assistant** and **code generation** plugin for IntelliJ IDEA, serving as a
powerful tool for developers.
It provides seamless integration with various LLM providers including **OpenAI (ChatGPT), DeepSeek, Claude, Gemini, and
Ollama** (local models).
OverCode empowers developers to **generate code, refactor, explain complex logic, detect bugs, and translate text**
directly within the IDE seamlessly.

## 🌟 核心特性 (Key Features)

### 🤖 强大的 LLM 集成能力

- 🏠 **沉浸式 AI 对话**：右侧工具栏内置 AI 聊天窗口，采用现代化 UI 设计，交互流畅。
- 🛠️ **广泛的模型支持**：
    - **OpenAI 兼容协议**：支持 OpenAI、DeepSeek、MoonShot、Zhipu、Qwen 等所有兼容 OpenAI 接口的服务
    - **原生 API 支持**：Gemini、Anthropic Claude、Minimax 等主流大模型
    - **本地模型**：Ollama 本地部署，代码不离机，隐私安全
- 🎨 **Prompt Playground**：
    - 内置 Prompt 编辑器，支持针对不同任务编写专用提示词。
    - 实时微调 Temperature、Context Window 等模型参数。

### ⚡ 深度 IDEA 集成 - 超越普通 AI 工具

OverCode 不仅仅是聊天机器人，它深度利用 IntelliJ IDEA 的强大能力：

- 🔍 **智能代码导航**：
    - **快速定位**：秒级查找类、方法、变量的定义位置
    - **引用追踪**：一键查看变量或方法在何处被引用，全面掌握代码依赖
    - **第三方库源码查看**：支持模糊搜索，直接查看第三方库中的源码实现

- 🚨 **实时错误检测**：
    - **编译爆红检测**：利用 IDEA 自身能力快速判断代码是否编译错误
    - **错误智能分析**：自动将错误信息发送到聊天窗口，AI 即时提供解决方案

- 📝 **代码变更管理**：
    - **修改追踪**：快速获取代码被修改的地方
    - **差异对比**：查看修改前后的代码对比，清晰了解变更内容

- 💬 **双向内容交互**：
    - **代码 → 聊天**：`Ctrl + Alt + I` 快速将选中的代码插入对话框
    - **聊天 → 代码**：一键将 AI 生成的代码插入到编辑器光标位置

- 🧠 **智能记忆系统**：
    - **项目记忆库**：自动保存项目特有的配置、架构约定、代码风格等重要信息
    - **自定义编辑**：支持手动编辑和管理记忆内容
    - **上下文感知**：AI 自动读取记忆，理解项目特殊性，提供更精准的建议

### 🎯 高效开发工作流

- ⚡ **快捷操作**：
    - **快捷插入**：`Ctrl + Alt + I` 将选中的代码瞬间同步至 AI 聊天框
    - **动态 AI 菜单**：右键菜单根据 Prompt 模板动态生成子菜单（重构、解释、纠错等）
    - **快速翻译**：`Ctrl + Alt + T` 选中即翻，弹窗即现
    - **异常发送**：在 Run/Console 窗口右键发送异常信息到聊天框分析

- 📊 **上下文感知**：自动提取选中的代码块或当前文件内容，让 AI 真正"读懂"你的代码

---
<!-- Plugin description end -->

## 💡 为什么选择 OverCode？

| 特性 | OverCode | 其他 AI 插件 |
| :--- | :--- | :--- |
| **IDEA 深度集成** | ✅ 完全利用 IDEA 原生能力 | ❌ 仅基础文本交互 |
| **代码导航能力** | ✅ 类/方法/变量快速定位 | ❌ 不支持 |
| **引用追踪** | ✅ 一键查看引用位置 | ❌ 不支持 |
| **编译错误检测** | ✅ 实时爆红检测 | ❌ 不支持 |
| **代码变更对比** | ✅ 修改追踪 + 差异对比 | ❌ 不支持 |
| **项目记忆系统** | ✅ 可编辑的自定义记忆库 | ❌ 无记忆或不可编辑 |
| **第三方库源码** | ✅ 支持模糊搜索查看 | ❌ 不支持 |
| **双向内容交互** | ✅ 代码↔聊天无缝切换 | ⚠️ 部分支持 |
| **免费开源** | ✅ 完全免费开源 | ❌ 大多收费 |
| **多模型支持** | ✅ 支持 10+ 主流大模型 | ⚠️ 支持有限 |

## 🚀 快速上手 (Quick Start)

### 1. 环境准备

OverCode 支持多种大模型服务，您可以根据需求选择：

- **云服务**：OpenAI、Gemini、Claude、DeepSeek、MoonShot、Zhipu、Qwen、Minimax 等
- **本地部署**：安装 [Ollama](https://ollama.com/) 运行本地模型
- **任意 OpenAI 兼容接口**：包括各类中转服务或私有化部署

只需在配置中填写对应的 API Endpoint 和 API Key 即可。

### 2. 安装插件

- **Marketplace**: 在 IntelliJ IDEA 插件市场搜索 `Over Code` 并安装。
- **手动安装**: 从 [Releases](https://github.com/bboygf/over-code/releases) 下载 `OverCode.zip`，通过
  `Install Plugin from Disk...` 安装。

### 3. 配置与调优

进入 `Settings/Preferences` -> `Tools` -> `Over Code 配置`:

- **选择模型提供商**：支持 OpenAI、Gemini、Claude、Ollama、DeepSeek、MoonShot、Zhipu、Qwen、Minimax 等
- **Endpoint**: 设置 API 地址（不同提供商默认地址不同，可自动填充）
- **模型选择**: 输入模型名称（如 `gpt-4`, `claude-opus`, `gemini-pro`, `llama3` 等）
- **代理设置**：一键启用系统代理或自定义代理地址，轻松访问海外模型服务
- **Prompt 管理**: 在模板库中预设你的常用开发指令
- **记忆管理**: 查看和管理项目记忆库，自定义项目特定规则

---

## ⌨️ 常用快捷键 (Shortcuts)

| 功能 | 操作方式 | 描述 |
| :--- | :--- | :--- |
| **插入到聊天框** | 鼠标右键 或 `Ctrl + Alt + I` | 将选中的代码块发送到 AI 聊天输入框 |
| **快速翻译** | 鼠标右键 或 `Ctrl + Alt + T` | 在编辑器中直接弹窗翻译选中的文本 |
| **AI 助手菜单** | 鼠标右键 | 访问动态生成的 Prompts 菜单（重构、解释、纠错等） |
| **发送异常到聊天** | 鼠标右键 (Run/Console 窗口) | 将异常信息发送到聊天框分析 |

> 💡 **提示**:
> - 所有功能都可以通过**鼠标右键菜单**快速访问
> - 快捷键可以在 IDEA 设置中自定义修改
> - 配合 IDEA 原生快捷键使用效果更佳：
    >

- `Alt + F7`: 查看引用 (Find Usages)

> - `Ctrl + B`: 跳转到定义 (Go to Declaration)
    >
- `Ctrl + Shift + F10`: 运行当前文件 (Run)

---

## 🛠️ 技术栈 (Tech Stack)

- **Language**: Kotlin
- **Framework**: IntelliJ Platform SDK
- **Database**: SQLite (Exposed ORM)
- **Network**: Ktor Client
- **AI Integration**: OpenAI API, Gemini API, Anthropic Claude API, Ollama 及更多 OpenAI 兼容接口

## 💻 支持的语言与框架

OverCode 深度集成于 IntelliJ IDEA，完美支持：

- ☕ **Java**: 全面的 Java 代码分析、重构、调试支持
- 🎯 **Kotlin**: 原生 Kotlin 支持，包括协程、扩展函数等特性
- 🌐 **其他 JVM 语言**: Groovy、Scala 等（通过 IDEA 平台支持）
- 📦 **主流框架**: Spring Boot、MyBatis、Hibernate 等（通过 IDEA 智能提示）

---

## 📖 使用场景示例

### 场景 1：快速理解陌生代码

1. 选中不理解的代码段
2. `Ctrl + Alt + I` 发送到聊天框
3. AI 结合项目记忆库，提供针对性解释
4. 使用引用追踪查看代码调用链

### 场景 2：修复编译错误

1. 代码编译爆红
2. OverCode 自动检测错误并发送到聊天窗口
3. AI 分析错误原因并提供修复方案
4. 一键应用修复建议

### 场景 3：代码重构

1. 查看方法的所有引用位置
2. AI 分析重构影响范围
3. 生成重构方案并对比修改前后差异
4. 确认无误后应用更改

### 场景 4：学习第三方库

1. 模糊搜索第三方库类名
2. 直接查看源码实现
3. AI 解释核心逻辑和使用方法
4. 保存到项目记忆库供后续参考

### 场景 5：调试异常

1. 在 Run/Console 窗口选中异常堆栈
2. 右键选择"Over Code: 发送异常到聊天"
3. AI 分析异常原因并提供解决方案

---

## 🤝 贡献与反馈

如果你在使用过程中发现 bug 或有新的功能想法，欢迎提交 [Issue](https://github.com/bboygf/over-code/issues) 或 Pull
Request！

## 📬 联系我

- **Author**: [GuoFan](https://github.com/bboygf)
- **Email**: [fanguo922@gmail.com](mailto:fanguo922@gmail.com)
- **Website**: [https://felinetech.cn/](https://felinetech.cn/)
- **GitHub**: [https://github.com/bboygf/over-code](https://github.com/bboygf/over-code)
- **插件市场**: [JetBrains Plugin Marketplace](https://plugins.jetbrains.com/plugin/30117-overcode)

如有任何问题、建议或商务合作，欢迎随时联系！

---

## 📄 许可证

MIT License - 完全免费开源，可自由使用和修改

---

© 2025 Over Code. Built with ❤️ for developers.
