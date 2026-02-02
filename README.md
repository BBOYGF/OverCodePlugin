# Over Code 🚀

> **"Don't just write code, OverCode it."** —— 深度集成于 IntelliJ IDEA 的下一代 AI 编程助手，赋予开发者完全的 AI 自主权。

[![Platform](https://img.shields.io/badge/Platform-IntelliJ-blue.svg)](https://plugins.jetbrains.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---
<!-- Plugin description -->
OverCode is an intelligent AI assistant plugin for IntelliJ IDEA.
It provides seamless integration with various LLM providers (OpenAI, Gemini, Ollama)
to help developers generate code, translate text, and explain complex logic directly in the IDE.
## 🌟 核心特性 (Key Features)

- 🏠 **沉浸式 AI 对话**：右侧工具栏内置 AI 聊天窗口，支持 Compose Multiplatform 构建的现代 UI 体验。
- 🛠️ **本地与云端自由切换**：
    - **Ollama 支持**：完美衔接本地运行的 Llama 3, DeepSeek, CodeQwen 等模型，代码不离机。
    - **OpenAI 标准协议**：兼容所有 OpenAI 标准接口，一键接入个人 API Key 或三方中转。
- ⚡ **深度编辑器集成**：
    - **快捷插入**：`Ctrl + Alt + I` 将选中的代码瞬间同步至 AI 聊天框。
    - **动态 AI 组**：右键菜单根据 Prompt 模板动态生成子菜单（重构、解释、纠错等）。
    - **快速翻译**：`Ctrl + Alt + T` 选中即翻，弹窗即现。
- 🎨 **Prompt Playground**：
    - 内置 Prompt 编辑器，支持针对不同任务编写专用提示词。
    - 实时微调 Temperature、Context Window 等模型参数。
- 📊 **上下文感知**：自动提取选中的代码块或当前文件内容，让 AI 真正“读懂”你的代码。

---
<!-- Plugin description end -->
## 🚀 快速上手 (Quick Start)

### 1. 环境准备

确保你已拥有以下任一环境：

- 本地已安装并启动 [Ollama](https://ollama.com/)。
- 拥有有效的 OpenAI 或兼容平台的 API Key。

### 2. 安装插件

- **Marketplace**: 在 IntelliJ IDEA 插件市场搜索 `Over Code` 并安装。
- **手动安装**: 从 [Releases](https://github.com/bboygf/over-code/releases) 下载 `OverCode.zip`，通过
  `Install Plugin from Disk...` 安装。

### 3. 配置与调优

进入 `Settings/Preferences` -> `Tools` -> `Over Code 配置`:

- **模型源**: 选择 `Ollama` 或 `OpenAI` 协议。
- **Endpoint**: 设置地址（Ollama 默认：`http://localhost:11434`）。
- **模型选择**: 输入模型名称（如 `llama3`, `deepseek-v3`）。
- **Prompt 管理**: 在模板库中预设你的常用开发指令。

---

## ⌨️ 常用快捷键 (Shortcuts)

| 功能 | 快捷键 | 描述 |
| :--- | :--- | :--- |
| **插入到聊天框** | `Ctrl + Alt + I` | 将选中的代码块发送到 AI 聊天输入框 |
| **快速翻译** | `Ctrl + Alt + T` | 在编辑器中直接弹窗翻译选中的文本 |
| **AI 助手菜单** | `Right Click` | 访问动态生成的 Prompts 菜单 |

---

## 🛠️ 技术栈 (Tech Stack)

- **Language**: Kotlin
- **Framework**: IntelliJ Platform SDK
- **UI**: Compose Multiplatform
- **Database**: SQLite (Exposed ORM)
- **Network**: Ktor Client

---

## 🤝 贡献与反馈

如果你在使用过程中发现 bug 或有新的功能想法，欢迎提交 [Issue](https://github.com/bboygf/over-code/issues) 或 Pull
Request！

**Author**: [GuoFan](https://github.com/bboygf)

---

© 2025 Over Code. Built with ❤️ for developers.
